package ca.uhn.fhir.model.primitive;

import static ca.uhn.fhir.model.api.TemporalPrecisionEnum.*;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.lang3.time.FastDateFormat;

import ca.uhn.fhir.model.api.BasePrimitive;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.parser.DataFormatException;

public abstract class BaseDateTimeDt extends BasePrimitive<Date> {

	private static final FastDateFormat ourYearFormat = FastDateFormat.getInstance("yyyy");
	private static final FastDateFormat ourYearMonthDayFormat = FastDateFormat.getInstance("yyyy-MM-dd");
	private static final FastDateFormat ourYearMonthFormat = FastDateFormat.getInstance("yyyy-MM");
	private static final FastDateFormat ourYearMonthDayNoDashesFormat = FastDateFormat.getInstance("yyyyMMdd");
	private static final FastDateFormat ourYearMonthDayTimeFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss");
	private static final FastDateFormat ourYearMonthDayTimeZoneFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ssZZ");
	private static final FastDateFormat ourYearMonthDayTimeMilliZoneFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
	private static final FastDateFormat ourYearMonthDayTimeMilliFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSS");

	private TemporalPrecisionEnum myPrecision = TemporalPrecisionEnum.SECOND;
	private Date myValue;
	private TimeZone myTimeZone;
	private boolean myTimeZoneZulu = false;

	/**
	 * Gets the precision for this datatype using field values from
	 * {@link Calendar}, such as {@link Calendar#MONTH}. Default is
	 * {@link Calendar#DAY_OF_MONTH}
	 * 
	 * @see #setPrecision(int)
	 */
	public TemporalPrecisionEnum getPrecision() {
		return myPrecision;
	}

	@Override
	public Date getValue() {
		return myValue;
	}

	@Override
	public String getValueAsString() {
		if (myValue == null) {
			return null;
		} else {
			switch (myPrecision) {
			case DAY:
				return ourYearMonthDayFormat.format(myValue);
			case MONTH:
				return ourYearMonthFormat.format(myValue);
			case YEAR:
				return ourYearFormat.format(myValue);
			case SECOND:
				if (myTimeZoneZulu) {
					GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
					cal.setTime(myValue);
					return ourYearMonthDayTimeFormat.format(cal) + "Z";
				} else if (myTimeZone != null) {
					GregorianCalendar cal = new GregorianCalendar(myTimeZone);
					cal.setTime(myValue);
					return ourYearMonthDayTimeZoneFormat.format(cal);
				} else {
					return ourYearMonthDayTimeFormat.format(myValue);
				}
			case MILLI:
				if (myTimeZoneZulu) {
					GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
					cal.setTime(myValue);
					return ourYearMonthDayTimeMilliFormat.format(cal) + "Z";
				} else if (myTimeZone != null) {
					GregorianCalendar cal = new GregorianCalendar(myTimeZone);
					cal.setTime(myValue);
					return ourYearMonthDayTimeMilliZoneFormat.format(cal);
				} else {
					return ourYearMonthDayTimeMilliFormat.format(myValue);
				}
			}
			throw new IllegalStateException("Invalid precition (this is a HAPI bug, shouldn't happen): " + myPrecision);
		}
	}

	/**
	 * Sets the precision for this datatype using field values from
	 * {@link Calendar}. Valid values are:
	 * <ul>
	 * <li>{@link Calendar#SECOND}
	 * <li>{@link Calendar#DAY_OF_MONTH}
	 * <li>{@link Calendar#MONTH}
	 * <li>{@link Calendar#YEAR}
	 * </ul>
	 * 
	 * @throws DataFormatException
	 */
	public void setPrecision(TemporalPrecisionEnum thePrecision) throws DataFormatException {
		if (thePrecision == null) {
			throw new NullPointerException("Precision may not be null");
		}
		myPrecision = thePrecision;
	}

	@Override
	public void setValue(Date theValue) throws DataFormatException {
		myValue = theValue;
	}

	@Override
	public void setValueAsString(String theValue) throws DataFormatException {
		try {
			if (theValue == null) {
				myValue = null;
				clearTimeZone();
			} else if (theValue.length() == 4) {
				if (isPrecisionAllowed(YEAR)) {
					setValue((ourYearFormat).parse(theValue));
					setPrecision(YEAR);
					clearTimeZone();
				} else {
					throw new DataFormatException("Invalid date/time string (datatype " + getClass().getSimpleName() + " does not support YEAR precision): " + theValue);
				}
			} else if (theValue.length() == 7) {
				// E.g. 1984-01 (this is valid according to the spec)
				if (isPrecisionAllowed(MONTH)) {
					setValue((ourYearMonthFormat).parse(theValue));
					setPrecision(MONTH);
					clearTimeZone();
				} else {
					throw new DataFormatException("Invalid date/time string (datatype " + getClass().getSimpleName() + " does not support MONTH precision): " + theValue);
				}
			} else if (theValue.length() == 8) {
				//Eg. 19840101 (allow this just to be lenient)
				if (isPrecisionAllowed(DAY)) {
					setValue((ourYearMonthDayNoDashesFormat).parse(theValue));
					setPrecision(MONTH);
					clearTimeZone();
				} else {
					throw new DataFormatException("Invalid date/time string (datatype " + getClass().getSimpleName() + " does not support DAY precision): " + theValue);
				}
			} else if (theValue.length() == 10) {
				// E.g. 1984-01-01 (this is valid according to the spec)
				if (isPrecisionAllowed(DAY)) {
					setValue((ourYearMonthDayFormat).parse(theValue));
					setPrecision(DAY);
					clearTimeZone();
				} else {
					throw new DataFormatException("Invalid date/time string (datatype " + getClass().getSimpleName() + " does not support DAY precision): " + theValue);
				}
			} else if (theValue.length() >= 18) {
				int dotIndex = theValue.indexOf('.', 18);
				if (dotIndex == -1 && !isPrecisionAllowed(SECOND)) {
					throw new DataFormatException("Invalid date/time string (data type does not support SECONDS precision): " + theValue);
				} else if (dotIndex > -1 && !isPrecisionAllowed(MILLI)) {
					throw new DataFormatException("Invalid date/time string (data type " + getClass().getSimpleName() + " does not support MILLIS precision):" + theValue);
				}

				Calendar cal;
				try {
					cal = DatatypeConverter.parseDateTime(theValue);
				} catch (IllegalArgumentException e) {
					throw new DataFormatException("Invalid data/time string (" + e.getMessage() + "): " + theValue);
				}
				myValue = cal.getTime();
				if (dotIndex == -1) {
					setPrecision(TemporalPrecisionEnum.SECOND);
				} else {
					setPrecision(TemporalPrecisionEnum.MILLI);
				}

				clearTimeZone();
				if (theValue.endsWith("Z")) {
					myTimeZoneZulu = true;
				} else if (theValue.indexOf('+', 19) != -1 || theValue.indexOf('-', 19) != -1) {
					myTimeZone = cal.getTimeZone();
				}

			} else {
				throw new DataFormatException("Invalid date/time string (invalid length): " + theValue);
			}
		} catch (ParseException e) {
			throw new DataFormatException("Invalid date string (" + e.getMessage() + "): " + theValue);
		}
	}

	public TimeZone getTimeZone() {
		return myTimeZone;
	}

	public void setTimeZone(TimeZone theTimeZone) {
		myTimeZone = theTimeZone;
	}

	public boolean isTimeZoneZulu() {
		return myTimeZoneZulu;
	}

	public void setTimeZoneZulu(boolean theTimeZoneZulu) {
		myTimeZoneZulu = theTimeZoneZulu;
	}

	private void clearTimeZone() {
		myTimeZone = null;
		myTimeZoneZulu = false;
	}

	/**
	 * To be implemented by subclasses to indicate whether the given precision
	 * is allowed by this type
	 */
	abstract boolean isPrecisionAllowed(TemporalPrecisionEnum thePrecision);

}
