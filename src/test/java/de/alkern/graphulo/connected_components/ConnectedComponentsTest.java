package de.alkern.graphulo.connected_components;

import de.alkern.author.AuthorProcessor;
import de.alkern.graphulo.GraphuloConnector;
import de.alkern.infrastructure.ExampleData;
import de.alkern.infrastructure.GraphuloProcessor;
import de.alkern.infrastructure.connector.AccumuloConnector;
import de.alkern.infrastructure.entry.AdjacencyEntry;
import de.alkern.infrastructure.repository.Repository;
import de.alkern.infrastructure.repository.RepositoryImpl;
import edu.mit.ll.graphulo.DynamicIteratorSetting;
import edu.mit.ll.graphulo.Graphulo;
import edu.mit.ll.graphulo.skvi.CountAllIterator;
import edu.mit.ll.graphulo.skvi.DynamicIterator;
import org.apache.accumulo.core.client.*;
import org.apache.accumulo.core.client.admin.TableOperations;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.iterators.LongCombiner;
import org.apache.accumulo.core.iterators.system.CountingIterator;
import org.apache.accumulo.core.security.Authorizations;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

public class ConnectedComponentsTest {

    private static Graphulo graphulo;
    private static TableOperations operations;

    @BeforeClass
    public static void init() throws Exception {
        //load test data into acccumulo
        Connector conn = AccumuloConnector.local();
        graphulo = GraphuloConnector.local(conn);
        operations = conn.tableOperations();
        if (!(operations.exists("test") || operations.exists("test_deg"))) {
           fillDatabase(conn);
        }
    }

    private static void fillDatabase(Connector conn) throws Exception {
        Repository repo = new RepositoryImpl("test", conn, new AdjacencyEntry.AdjacencyBuilder());
        GraphuloProcessor processor = new AuthorProcessor(repo);
        processor.parse(ExampleData.TWO_COMPONENTS_EXAMPLE);
        graphulo.generateDegreeTable("test", "test_deg", false);
    }

    @Test
    public void find() throws Exception {
        //splitConnectedComponents connected components
        new ConnectedComponents(graphulo).splitConnectedComponents( "test", "test_deg");
        assertTrue(operations.exists("test_cc1"));
        assertTrue(operations.exists("test_cc2"));
        assertFalse(operations.exists("test_cc3"));

        assertEquals(6, countEntries("test_cc1"));
        assertEquals(2, countEntries("test_cc2"));

        //clean up
//        operations.delete("test");
//        operations.delete("test_deg");
        operations.delete("test_cc1");
        operations.delete("test_cc2");
    }

    private long countEntries(String table) throws TableNotFoundException {
        BatchScanner scanner = graphulo.getConnector().createBatchScanner(table, Authorizations.EMPTY, 5);
        scanner.setRanges(Collections.singleton(new Range()));
        scanner.addScanIterator(new IteratorSetting(1, CountAllIterator.class));
        long cnt = 0;
        for (Map.Entry<Key, Value> entry : scanner) {
            cnt += LongCombiner.STRING_ENCODER.decode(entry.getValue().get());
        }
        scanner.close();
        return cnt;
    }

    @Test
    public void testGetNeighbours() throws AccumuloSecurityException, AccumuloException {
        Collection<String> neighbours = new ConnectedComponents(graphulo).getNeighbours( "test",
                "test_deg", "Artikel1 Autor1,");
        assertEquals(2, neighbours.size());
    }

}