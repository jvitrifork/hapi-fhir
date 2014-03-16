package ca.uhn.fhir.context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.uhn.fhir.model.api.BaseResourceReference;
import ca.uhn.fhir.model.api.IElement;
import ca.uhn.fhir.model.api.IResource;

public class RuntimeResourceReferenceDefinition extends BaseRuntimeElementDefinition<BaseResourceReference> {

	private final List<Class<? extends IResource>> myResourceTypes;
	private HashMap<Class<? extends IResource>, RuntimeResourceDefinition> myResourceTypeToDefinition;

	public RuntimeResourceReferenceDefinition(String theName, List<Class<? extends IResource>> theResourceTypes) {
		super(theName, BaseResourceReference.class);
		if (theResourceTypes == null || theResourceTypes.isEmpty()) {
			throw new ConfigurationException("Element '" + theName + "' has no resource types noted");
		}
		myResourceTypes = theResourceTypes;
	}

	public List<Class<? extends IResource>> getResourceTypes() {
		return myResourceTypes;
	}

	@Override
	void sealAndInitialize(Map<Class<? extends IElement>, BaseRuntimeElementDefinition<?>> theClassToElementDefinitions) {
		myResourceTypeToDefinition = new HashMap<Class<? extends IResource>, RuntimeResourceDefinition>();
		for (Class<? extends IResource> next : myResourceTypes) {
			if (next.equals(IResource.class)) {
				continue;
			}
			RuntimeResourceDefinition definition = (RuntimeResourceDefinition) theClassToElementDefinitions.get(next);
			if (definition == null) {
				throw new ConfigurationException("Couldn't find definition for: " + next.getCanonicalName());
			}
			myResourceTypeToDefinition.put(next, definition);
		}
	}

	@Override
	public ca.uhn.fhir.context.BaseRuntimeElementDefinition.ChildTypeEnum getChildType() {
		return ChildTypeEnum.RESOURCE_REF;
	}

	public RuntimeResourceDefinition getDefinitionForResourceType(Class<? extends IResource> theType) {
		RuntimeResourceDefinition retVal = myResourceTypeToDefinition.get(theType);
		if (retVal == null) {
			throw new ConfigurationException("Unknown type:  " + theType.getCanonicalName());
		}
		return retVal;
	}

}
