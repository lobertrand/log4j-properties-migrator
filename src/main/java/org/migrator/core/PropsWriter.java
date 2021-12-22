package org.migrator.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.migrator.model.Log4j1Properties;
import org.migrator.model.Log4j1RootLogger;
import org.migrator.model.NumberedValue;

public class PropsWriter {

	private static final String FILE_APPENDER = "org.apache.log4j.FileAppender";
	private static final String DAILY_ROLLING_FILE_APPENDER = "org.apache.log4j.DailyRollingFileAppender";
	private static final String ROLLING_FILE_APPENDER = "org.apache.log4j.RollingFileAppender";
	private static final String CONSOLE_APPENDER = "org.apache.log4j.ConsoleAppender";

	private static final String PATTERN_LAYOUT = "org.apache.log4j.PatternLayout";

	/**
	 * Transforms a Log41 properties object into a Log4j2 properties file. Orders the output lines so that they appear in the same order as
	 * their related lines in the original properties file.
	 * 
	 * @param properties Log4j1 properties object
	 * @return Lines of the Log4j2 properties file
	 */
	public static List<String> writeLog4j2Properties(Log4j1Properties properties, List<String> originalFile) {
		List<NumberedValue> output = new ArrayList<>();

		// Root logger
		Log4j1RootLogger rootLogger = properties.rootLogger;
		if (rootLogger.level != null) {
			output.add(new NumberedValue("rootLogger.level = " + rootLogger.level.value, rootLogger.level.lineNumber));
		}
		for (NumberedValue appenderName : properties.rootLogger.appenderNames) {
			output.add(new NumberedValue("rootLogger.appenderRef." + nameToProp(appenderName.value) + ".ref = " + appenderName.value,
					appenderName.lineNumber));
		}

		// Other loggers
		properties.getLoggers().forEach((loggerName, logger) -> {
			String loggerProp = nameToProp(loggerName);
			String loggerPrefix = "logger." + loggerProp;

			output.add(new NumberedValue(loggerPrefix + ".name = " + logger.name.value, logger.name.lineNumber));
			if (logger.level != null) {
				output.add(new NumberedValue(loggerPrefix + ".level = " + logger.level.value, logger.level.lineNumber));
			}
			if (logger.additivity != null) {
				output.add(new NumberedValue(loggerPrefix + ".additivity = " + logger.additivity.value, logger.additivity.lineNumber));
			}
			for (NumberedValue appenderName : logger.appenderNames) {
				output.add(new NumberedValue(loggerPrefix + ".appenderRef." + nameToProp(appenderName.value)
						+ ".ref = " + appenderName.value, appenderName.lineNumber));
			}
		});

		// Appenders
		properties.getAppenders().forEach((appenderName, appender) -> {
			String appenderProp = nameToProp(appenderName);
			String appenderPrefix = "appender." + appenderProp;

			// Type
			if (appender.typeClass == null) {
				handleWriteError("Missing property 'type'", appender.name.lineNumber, properties, originalFile);

			} else {
				String appenderType = convertAppenderType(appender.typeClass.value, appender.typeClass.lineNumber);
				output.add(new NumberedValue(appenderPrefix + ".name = " + appenderName, appender.name.lineNumber));
				output.add(new NumberedValue(appenderPrefix + ".type = " + appenderType, appender.typeClass.lineNumber));

				// Info related to appender type
				if (appender.typeClass.value.equals(ROLLING_FILE_APPENDER)) {
					if (appender.file == null) {
						handleWriteError("Missing property 'file'", appender.name.lineNumber, properties, originalFile);
					} else {
						output.add(new NumberedValue(appenderPrefix + ".fileName = " + appender.file.value, appender.file.lineNumber));
						output.add(new NumberedValue(appenderPrefix + ".filePattern = " + appender.file.value + ".%i",
								appender.file.lineNumber));
					}

				} else if (appender.typeClass.value.equals(DAILY_ROLLING_FILE_APPENDER)) {
					if (appender.file == null) {
						handleWriteError("Missing property 'file'", appender.name.lineNumber, properties, originalFile);
					} else if (appender.datePattern == null) {
						handleWriteError("Missing property 'datePattern'", appender.name.lineNumber, properties, originalFile);
					} else {
						output.add(new NumberedValue(appenderPrefix + ".fileName = " + appender.file.value, appender.file.lineNumber));
						String dateSuffix = ".%d{" + appender.datePattern.value.replaceAll("'\\.'", "") + "}";
						output.add(new NumberedValue(appenderPrefix + ".filePattern = " + appender.file.value + dateSuffix,
								appender.file.lineNumber));
						output.add(new NumberedValue(appenderPrefix + ".policies.type = Policies", appender.datePattern.lineNumber));
						output.add(new NumberedValue(appenderPrefix + ".policies.time.type = TimeBasedTriggeringPolicy",
								appender.datePattern.lineNumber));
						output.add(new NumberedValue(appenderPrefix + ".policies.time.interval = 1", appender.datePattern.lineNumber));
					}

				} else if (appender.typeClass.value.equals(FILE_APPENDER)) {
					if (appender.file == null) {
						handleWriteError("Missing property 'file'", appender.name.lineNumber, properties, originalFile);
					} else {
						output.add(new NumberedValue(appenderPrefix + ".fileName = " + appender.file.value, appender.file.lineNumber));
					}
				}
			}

			// Append
			if (appender.append != null) {
				output.add(new NumberedValue(appenderPrefix + ".append = " + appender.append.value, appender.append.lineNumber));
			}

			// Encoding
			if (appender.encoding != null) {
				output.add(new NumberedValue(appenderPrefix + ".encoding = " + appender.encoding.value, appender.encoding.lineNumber));
			}

			// Layout
			if (appender.layout != null) {
				String appenderLayoutType = convertAppenderLayoutType(appender.layout.value, appender.layout.lineNumber);
				output.add(new NumberedValue(appenderPrefix + ".layout.type = " + appenderLayoutType, appender.layout.lineNumber));

				if (appender.layout.value.equals(PATTERN_LAYOUT)) {
					if (appender.layoutConversionPattern != null) {
						output.add(new NumberedValue(appenderPrefix + ".layout.pattern = " + appender.layoutConversionPattern.value,
								appender.layoutConversionPattern.lineNumber));
					} else {
						handleWriteError("Missing layout pattern for appender", appender.name.lineNumber, properties, originalFile);
					}
				}
			}

			// Max file size
			if (appender.maxFileSize != null) {
				output.add(new NumberedValue(appenderPrefix + ".policies.type = Policies", appender.maxFileSize.lineNumber));
				output.add(new NumberedValue(appenderPrefix + ".policies.size.type = SizeBasedTriggeringPolicy",
						appender.maxFileSize.lineNumber));
				output.add(new NumberedValue(appenderPrefix + ".policies.size.size = " + appender.maxFileSize.value,
						appender.maxFileSize.lineNumber));
			}

			// Max backup index
			if (appender.maxBackupIndex != null) {
				output.add(new NumberedValue(appenderPrefix + ".strategy.type = DefaultRolloverStrategy",
						appender.maxBackupIndex.lineNumber));
				output.add(new NumberedValue(appenderPrefix + ".strategy.max = " + appender.maxBackupIndex.value,
						appender.maxBackupIndex.lineNumber));
			}

			// Threshold
			if (appender.threshold != null) {
				output.add(new NumberedValue(appenderPrefix + ".filter.threshold.type = ThresholdFilter", appender.threshold.lineNumber));
				output.add(new NumberedValue(appenderPrefix + ".filter.threshold.level = " + appender.threshold.value,
						appender.threshold.lineNumber));
			}
		});

		// Comments
		output.addAll(properties.comments);

		// Parsing errors
		output.addAll(properties.errors);

		return output.stream()
				.sorted(Comparator.comparingInt(NumberedValue::getLineNumber))
				.map(NumberedValue::getValue)
				.collect(Collectors.toList());
	}

	private static String nameToProp(String name) {
		return name.replaceAll("\\.", "_").toLowerCase();
	}

	private static String convertAppenderLayoutType(String layout, int lineNumber) {
		if (layout == null) {
			System.err.println("Missing layout type at line " + lineNumber);
			return "[Missing layout type]";
		}
		switch (layout) {
		case PATTERN_LAYOUT:
			return "PatternLayout";
		default:
			System.err.println("Unknown layout type at line " + lineNumber + ": " + layout);
			return "[Unknown layout type: " + layout + "]";
		}
	}

	private static String convertAppenderType(String classType, int lineNumber) {
		if (classType == null) {
			System.err.println("Missing appender type at line " + lineNumber);
			return "[Missing appender type]";
		}
		switch (classType) {
		case CONSOLE_APPENDER:
			return "Console";
		case ROLLING_FILE_APPENDER:
			return "RollingFile";
		case DAILY_ROLLING_FILE_APPENDER:
			return "RollingFile";
		case FILE_APPENDER:
			return "File";
		default:
			System.err.println("Unknown appender type at line " + lineNumber + ": " + classType);
			return "[Unknown appender type: " + classType + "]";
		}
	}

	/*
	 * Print error message to stderr and collect error into the properties object, with its line number.
	 */
	private static void handleWriteError(String message, int lineNumber, Log4j1Properties properties, List<String> originalFile) {
		String line = originalFile.get(lineNumber - 1);
		System.err.println(message + " at line " + lineNumber + " : " + line);
		properties.errors.add(new NumberedValue("# [MigratorError: " + message + "] " + line, lineNumber));
	}

}
