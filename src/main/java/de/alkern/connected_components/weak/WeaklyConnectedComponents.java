package de.alkern.connected_components.weak;

import de.alkern.connected_components.ComponentType;
import de.alkern.connected_components.ConnectedComponentsUtils;
import de.alkern.connected_components.data.VisitedNodes;
import edu.mit.ll.graphulo.Graphulo;
import edu.mit.ll.graphulo.skvi.RemoteWriteIterator;
import edu.mit.ll.graphulo.util.GraphuloUtil;
import org.apache.accumulo.core.client.*;
import org.apache.accumulo.core.client.admin.TableOperations;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;

import java.util.Arrays;
import java.util.Map;
import java.util.SortedSet;

/**
 * Algorithm to get all weakly connected components from an adjacency matrix.
 * Weakly connected components are all nodes which have some kind of connection in between.
 * If the graph is undirected it is also a strongly connected component.
 * Every connected component is saved in a table named original table + _wcc + number
 */
public class WeaklyConnectedComponents {

    private Graphulo g;
    private Connector conn;
    private TableOperations tops;
    private String table;
    private Components components;
    private VisitedNodes alreadyVisited;

    public WeaklyConnectedComponents(Graphulo g, VisitedNodes visited) {
        this.g = g;
        this.conn = g.getConnector();
        this.tops = conn.tableOperations();
        this.components = new Components();
        this.alreadyVisited = visited;
    }

    public void calculateConnectedComponents(String table) {
       try {
           //reset class variables for this run
           this.table = table;
           this.components.clear();
           this.alreadyVisited.clear();
           Scanner scanner = conn.createScanner(table, Authorizations.EMPTY);
           scanner.setRange(new Range());
           //iterate over every row in the table
           RowIterator rowIterator = new RowIterator(scanner);
           while (rowIterator.hasNext()) {
               visit(rowIterator.next().next());  //visit each row exactly once
           }
           scanner.close();
           copyComponents();
       } catch (Exception e) {
           throw new RuntimeException("Could not calculate weakly ccs", e);
       }
    }

    /**
     * Visit an entry in the table and calculate the whole component that belongs to this
     * @param entry
     */
    private void visit(Map.Entry<Key, Value> entry) {
        String row = entry.getKey().getRow().toString();
        if (alreadyVisited.hasVisited(row)) {
            return;
        }
        String newNeighbours = ConnectedComponentsUtils.getAllNeighbours(g, table, row);
        //add all nodes in neighbourhood to alreadyVisited
        alreadyVisited.visitNode(row);
        Arrays.stream(newNeighbours.split(";")).forEach(alreadyVisited::visitNode);
        components.put(row, newNeighbours);
    }

    /**
     * copy the calculated components in new tables
     */
    private void copyComponents() throws TableNotFoundException, TableExistsException, AccumuloSecurityException, AccumuloException {
        int componentNumber = 1;
        for (Map.Entry<String, String> entry: components.entrySet()) {
            SortedSet<Range> rangeSet = GraphuloUtil.d4mRowToRanges(entry.getValue());
            rangeSet.add(new Range(entry.getKey()));

            String ccName = ConnectedComponentsUtils.getComponentTableName(table, ComponentType.WEAK,
                    componentNumber++);
            BatchScanner bs  = ConnectedComponentsUtils.createBatchScanner(g, table, rangeSet);
            bs.addScanIterator(new IteratorSetting(10, "Writer for " + ccName, RemoteWriteIterator.class,
                    g.basicRemoteOpts("", ccName, null, null)));
            tops.create(ccName);
            for (Map.Entry<Key, Value> k : bs) {
                //visit every node and copy it -> could be used for something useful, e.g. get component size
            }
            bs.close();
        }
    }
}
