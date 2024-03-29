/*
 * Copyright (c) 2023, FPS BOSA
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package be.fgov.bosa.shaclvalidator;

import be.fgov.bosa.shaclvalidator.reports.Report;
import be.fgov.bosa.shaclvalidator.reports.ReportFactory;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.apache.commons.io.FilenameUtils;

import org.eclipse.rdf4j.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Command-line validator
 * 
 * @author Bart Hanssens
 */
@Command(name = "SHACL Validator", mixinStandardHelpOptions = true, version = "1.0",
         description = "Validates an RDF file using a (Turtle) SHACL file.")
public class Main implements Callable<Integer> {
	private final static Logger LOG = LoggerFactory.getLogger(Main.class);

    @Option(names = "--data", description = "Data file location (URL or local file)", required = true)
    URL data;

    @Option(names = "--format", description = "Data file format")
    Optional<String> format;

    @Option(names = "--shacl", description = "SHACL file(s) location (URL or local file)", required = true)
    URL[] shacl;

    @Option(names = "--report", description = "Write report to this file(s), format can be HTML, TTL or MD")
    Path[] reports;

    @Option(names = "--maxErrors", description = "Maximum number of reports to report")
    Optional<Integer> maxErrors;

    @Option(names = "--countClasses", description = "Count number of classes")
    boolean countClasses;

    @Option(names = "--countProperties", description = "Count number of predicates/properties")
    boolean countProperties;

    @Option(names = "--countValues", description = "Count number of values for one or more properties")
    String[] countValues;

	/**
	 * Write errors and statistics, if any
	 * 
	 * @param results
	 * @param stats
	 * @throws IOException
	 * @return status code
	 */
	private void writeReports(Model results, Map<String,Object> stats) throws IOException {
		for(Path report: reports) {
			LOG.info("Writing report to {}", report);
			String ext = FilenameUtils.getExtension(report.toString());
			
			Report tmpl = ReportFactory.createReport(ext);
			tmpl.reportValidation(results, data, shacl);
			tmpl.reportStatistics(stats);

			try(Writer w = Files.newBufferedWriter(report)) {
				tmpl.write(w);					
			}
		}
	}

	@Override
    public Integer call() throws Exception {
		try {
			Validator validator = new Validator(maxErrors.orElse(1000));
			Model results = validator.validate(shacl, data, format);
			Statistics statistics = new Statistics(validator.getRepository());
			Map<String,Object> stats = statistics.collect(countClasses, countProperties, countValues);
			writeReports(results, stats);
			
			if (Validator.countErrors(results) > 0) {
				return 1;
			}
			if (Validator.countWarnings(results) > 0) {
				return 2;
			}
			if (Validator.countInfos(results) > 0) {
				return 3;
			}
		} catch (IOException e) {
			LOG.error(e.getMessage());
			return -1;
		}
		return 0;
	}

	/**
	 * Main
	 * 
	 * @param args
	 */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
		System.exit(exitCode);
    }
}
