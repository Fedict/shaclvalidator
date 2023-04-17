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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.eclipse.rdf4j.common.exception.ValidationException;
import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.DCAT;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.FOAF;
import org.eclipse.rdf4j.model.vocabulary.ORG;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDF4J;
import org.eclipse.rdf4j.model.vocabulary.ROV;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.eclipse.rdf4j.model.vocabulary.VCARD4;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.shacl.ShaclSail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Validate an RDF data file (can be a local file or URL) using a SHACL file
 * 
 * @author Bart Hanssens
 */
public class Validator implements AutoCloseable {
	private final static Logger LOG = LoggerFactory.getLogger(Validator.class);
	
	public final static List<Namespace> NS = List.of(DCAT.NS, DCTERMS.NS, FOAF.NS, ORG.NS, RDF.NS, ROV.NS, SKOS.NS, VCARD4.NS);

	private final Repository repo;

	/**
	 * Load SHACL rules (Turtle)
	 * 
	 * @param location location of the SHACL file
	 * @throws IOException 
	 */
	private void loadShacl(URL location) throws IOException {
		LOG.info("Loading shacl from {}", location.toString());
		try (RepositoryConnection conn = repo.getConnection();
			BufferedInputStream bisShacl = new BufferedInputStream(location.openStream())) {

			conn.begin();
			conn.add(bisShacl, "", RDFFormat.TURTLE, RDF4J.SHACL_SHAPE_GRAPH);
			conn.commit();
		}
	}

	/**
	 * Validate a file
	 * 
	 * @param location location of the data
	 * @param fmt RDF format
	 * @return report with violations/warnings
	 * @throws IOException 
	 */
	private Model validate(URL location, Optional<String> fmt) throws IOException {
		LOG.info("Loading data from {}", location.toString());
		try (RepositoryConnection conn = repo.getConnection();
			BufferedInputStream bisData = new BufferedInputStream(location.openStream())) {
			
			Optional<RDFFormat> rdf = fmt.isPresent() 
				? Rio.getParserFormatForMIMEType(fmt.get())
				: Rio.getParserFormatForFileName(location.getFile());

			conn.begin(IsolationLevels.NONE, ShaclSail.TransactionSettings.ValidationApproach.Bulk);
			conn.add(bisData, rdf.orElse(RDFFormat.RDFXML));
			conn.commit();
		} catch (RepositoryException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof ValidationException validationException) {
				return validationException.validationReportAsModel();
			}
		}
		return new LinkedHashModel();
	}

	/**
	 * Validate an RDF data file (can be a local file or URL) using a SHACL file.
	 * Format is optional: when not present the format will be guessed based on the file extension.
	 * 
	 * @param shacl location of the SHACL file
	 * @param data location of the data
	 * @param format optional format
	 * @return model containing results
	 * @throws IOException 
	 */
	public Model validate(URL shacl, URL data, Optional<String> format) throws IOException {
		try (RepositoryConnection conn = repo.getConnection()) {
			conn.clear();
		}
		loadShacl(shacl);
		return validate(data, format);
	}

	/**
	 * Get the underlying repository;
	 * 
	 * @return 
	 */
	public Repository getRepository() {
		return repo;
	}

	@Override
	public void close() {
		if (repo != null) {
			repo.shutDown();
		}
	}

	/**
	 * Constructor
	 */
    public Validator() {
		ShaclSail shaclSail = new ShaclSail(new MemoryStore());
		repo = new SailRepository(shaclSail);
	}
}
