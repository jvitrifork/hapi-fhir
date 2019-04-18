package ca.uhn.fhir.jpa.dao.expunge;

import ca.uhn.fhir.jpa.dao.DaoConfig;
import ca.uhn.fhir.jpa.util.ExpungeOptions;
import ca.uhn.fhir.jpa.util.ExpungeOutcome;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Scope("prototype")
public class ExpungeRun implements Callable<ExpungeOutcome> {
	private static final Logger ourLog = LoggerFactory.getLogger(ExpungeService.class);

	@Autowired
	private PlatformTransactionManager myPlatformTransactionManager;
	@Autowired
	private ExpungeDaoService myExpungeDaoService;
	@Autowired
	private DaoConfig myDaoConfig;

	private final String myResourceName;
	private final Long myResourceId;
	private final Long myVersion;
	private final ExpungeOptions myExpungeOptions;
	private final AtomicInteger myRemainingCount;
	private TransactionTemplate myTxTemplate;

	public ExpungeRun(String theResourceName, Long theResourceId, Long theVersion, ExpungeOptions theExpungeOptions) {
		myResourceName = theResourceName;
		myResourceId = theResourceId;
		myVersion = theVersion;
		myExpungeOptions = theExpungeOptions;
		myRemainingCount = new AtomicInteger(myExpungeOptions.getLimit());
	}

	@PostConstruct
	private void setTxTemplate() {
		myTxTemplate = new TransactionTemplate(myPlatformTransactionManager);
		myTxTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
	}

	@Override
	public ExpungeOutcome call() {

		if (myExpungeOptions.isExpungeDeletedResources() && myVersion == null) {

			expungeDeletedResources();
			if (expungeLimitReached()) {
				return expungeOutcome();
			}
		}

		if (myExpungeOptions.isExpungeOldVersions()) {

			expungeOldVersions();
			if (expungeLimitReached()) {
				return expungeOutcome();
			}
		}

		return expungeOutcome();
	}

	private void expungeDeletedResources() {
		Slice<Long> resourceIds = findHistoricalVersionsOfDeletedResources();

		deleteSearchResultCacheEntries(resourceIds);

		deleteHistoricalVersions(resourceIds);
		if (expungeLimitReached()) {
			return;
		}

		deleteCurrentVersionsOfDeletedResources(resourceIds);
	}

	private boolean expungeLimitReached() {
		boolean expungeLimitReached = myRemainingCount.get() <= 0;
		if (expungeLimitReached) {
			ourLog.debug("Expunge limit has been hit - Stopping operation");
		}
		return expungeLimitReached;
	}

	private void expungeOldVersions() {
		Slice<Long> historicalIds = findHistoricalVersionsOfNonDeletedResources();

		myTxTemplate.execute(t -> {
			myExpungeDaoService.expungeHistoricalVersions(historicalIds, myRemainingCount);
			return null;
		});
	}

	private Slice<Long> findHistoricalVersionsOfNonDeletedResources() {
		return myTxTemplate.execute(t -> myExpungeDaoService.findHistoricalVersionsOfNonDeletedResources(myResourceName, myResourceId, myVersion, myRemainingCount.get()));
	}

	private void deleteCurrentVersionsOfDeletedResources(Slice<Long> theResourceIds) {
		myTxTemplate.execute(t -> {
			myExpungeDaoService.expungeCurrentVersionOfResources(theResourceIds, myRemainingCount);
			return null;
		});
	}

	private void deleteHistoricalVersions(Slice<Long> theResourceIds) {
		myTxTemplate.execute(t -> {
			myExpungeDaoService.expungeHistoricalVersionsOfIds(theResourceIds, myRemainingCount);
			return null;
		});
	}


	private void deleteSearchResultCacheEntries(Slice<Long> theResourceIds) {
		List<List<Long>> partitions = Lists.partition(theResourceIds.getContent(), myDaoConfig.getExpungeBatchSize());
		for (List<Long> nextPartition : partitions) {
			ourLog.info("Expunging any search results pointing to {} resources", nextPartition.size());

			myTxTemplate.execute(t -> {
				myExpungeDaoService.deleteByResourceIdPartitions(nextPartition);
				return null;
			});
		}
	}

	private Slice<Long> findHistoricalVersionsOfDeletedResources() {
		return myTxTemplate.execute(t -> myExpungeDaoService.findHistoricalVersionsOfDeletedResources(myResourceName, myResourceId, myRemainingCount.get()));
	}

	private ExpungeOutcome expungeOutcome() {
		return new ExpungeOutcome()
			.setDeletedCount(myExpungeOptions.getLimit() - myRemainingCount.get());
	}

}
