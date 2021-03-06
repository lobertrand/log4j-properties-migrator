package org.migrator;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.migrator.core.PropsParser;
import org.migrator.core.PropsWriter;
import org.migrator.core.Util;
import org.migrator.model.Log4j1Properties;

public class Main {

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("Usage: java -jar migrator.jar log4j.properties [log4j2.properties]");
			System.exit(1);
		}

		Path inputPath = new File(args[0]).toPath();
		Path outputPath = args.length == 2
				? new File(args[1]).toPath()
				: inputPath.getParent().resolve("log4j2.properties");

		List<String> lines = Util.readLines(inputPath);

		Log4j1Properties log4j1Props = PropsParser.parseLog4j1Properties(lines);
		List<String> log4j2Props = PropsWriter.writeLog4j2Properties(log4j1Props);

		Util.writeLines(log4j2Props, outputPath);
	}

}
