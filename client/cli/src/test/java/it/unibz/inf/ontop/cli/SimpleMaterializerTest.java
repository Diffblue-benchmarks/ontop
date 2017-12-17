package it.unibz.inf.ontop.cli;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;

public class SimpleMaterializerTest {

    // TODO We need to extend this test to import the contents of the mappings
    // into OWL and repeat everything taking form OWL

    private Connection conn;

    private Logger log = LoggerFactory.getLogger(this.getClass());
    private OWLOntology ontology;

    final String owlfile = "src/test/resources/test/simplemapping.owl";
    // final String obdafile = "src/test/resources/test/simplemapping.obda";

    @Before
    public void setUp() throws Exception {
		/*
		 * Initializing and H2 database with the stock exchange data
		 */
        // String driver = "org.h2.Driver";
        String url = "jdbc:h2:mem:materialization_test";
        String username = "sa";
        String password = "";

        conn = DriverManager.getConnection(url, username, password);
        Statement st = conn.createStatement();

        FileReader reader = new FileReader("src/test/resources/test/simplemapping-classify-h2.sql");
        BufferedReader in = new BufferedReader(reader);
        StringBuilder bf = new StringBuilder();
        String line = in.readLine();
        while (line != null) {
            bf.append(line);
            line = in.readLine();
        }

        st.executeUpdate(bf.toString());
        conn.commit();

        // Loading the OWL file
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        ontology = manager.loadOntologyFromOntologyDocument((new File(owlfile)));
    }

    @After
    public void tearDown() throws Exception {
        dropTables();
        conn.close();
    }

    private void dropTables() throws SQLException, IOException {

        Statement st = conn.createStatement();

        FileReader reader = new FileReader("src/test/resources/test/simplemapping-drop-h2.sql");
        BufferedReader in = new BufferedReader(reader);
        StringBuilder bf = new StringBuilder();
        String line = in.readLine();
        while (line != null) {
            bf.append(line);
            line = in.readLine();
        }

        st.executeUpdate(bf.toString());
        st.close();
        conn.commit();
    }

    @Test
    public void runMaterializationWithReasoning() throws Exception {
        String outFile = "src/test/resources/output/simplemapping_materialzed_with_reasnoing.owl";
        String ontoFile = "src/test/resources/test/simplemapping.owl";
        String mappingFile = "src/test/resources/test/simplemapping.obda";
        String propertiesFile = "src/test/resources/test/simplemapping.properties";
        Ontop.main("materialize", "-m", mappingFile, "-t", ontoFile,
                "-o", outFile, "-p", propertiesFile);
        assertEquals(5, numOfClassAssertions(outFile));
        assertEquals(0, numOfObjectPropertyAssertions(outFile));
        assertEquals(2, numOfAnnotationAssertions(outFile));
    }

    @Test
    public void runMaterializationWithoutReasoning() throws Exception {
        String outFile = "src/test/resources/output/simplemapping_materialzed_no_reasnoing.owl";
        String ontoFile = "src/test/resources/test/simplemapping.owl";
        String mappingFile = "src/test/resources/test/simplemapping.obda";
        String propertiesFile = "src/test/resources/test/simplemapping.properties";
        Ontop.main("materialize", "-m", mappingFile, "-t", ontoFile,
                "-o", outFile, "--disable-reasoning", "-p", propertiesFile);
        assertEquals(3, numOfClassAssertions(outFile));
        assertEquals(0, numOfObjectPropertyAssertions(outFile));
    }

    private int numOfClassAssertions(String owlFile) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(owlFile));
        return ontology.getAxioms(AxiomType.CLASS_ASSERTION).size();
    }

    private int numOfObjectPropertyAssertions(String owlFile) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(owlFile));
        return ontology.getAxioms(AxiomType.OBJECT_PROPERTY_ASSERTION).size();
    }

    private int numOfAnnotationAssertions(String owlFile) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(owlFile));
        return ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION).size();
    }

}
