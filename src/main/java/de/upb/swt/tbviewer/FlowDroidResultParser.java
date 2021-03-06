package de.upb.swt.tbviewer;

import com.ibm.wala.util.collections.Pair;
import de.foellix.aql.datastructure.Reference;
import de.foellix.aql.datastructure.Statement;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class FlowDroidResultParser {

  class Tags {

    public static final String root = "DataFlowResults";

    public static final String results = "Results";
    public static final String result = "Result";

    public static final String performanceData = "PerformanceData";
    public static final String performanceEntry = "PerformanceEntry";

    public static final String sink = "Sink";
    public static final String accessPath = "AccessPath";

    public static final String fields = "Fields";
    public static final String field = "Field";

    public static final String sources = "Sources";
    public static final String source = "Source";

    public static final String taintPath = "TaintPath";
    public static final String pathElement = "PathElement";
  }

  class Attributes {

    public static final String fileFormatVersion = "FileFormatVersion";
    public static final String terminationState = "TerminationState";
    public static final String statement = "Statement";
    public static final String method = "Method";
    public static final String linenumber = "LineNumber";

    public static final String value = "Value";
    public static final String type = "Type";
    public static final String taintSubFields = "TaintSubFields";

    public static final String category = "Category";

    public static final String name = "Name";
    public static final String id = "ID";
  }

  public static boolean hasFormat(String fileName)
      throws XMLStreamException, FileNotFoundException, FactoryConfigurationError {

    XMLStreamReader reader =
        XMLInputFactory.newInstance().createXMLStreamReader(new FileInputStream(fileName));
    while (reader.hasNext()) {
      reader.next();
      if (reader.hasName() && reader.isStartElement()) {
        if (reader.getLocalName().equals(Tags.root)) {
          return true;
        }
      }
    }
    return false;
  }

  public static Set<TaintFlow> readResultsWithPath(String fileName)
      throws XMLStreamException, IOException {
    XMLStreamReader reader =
        XMLInputFactory.newInstance().createXMLStreamReader(new FileInputStream(fileName));
    Set<TaintFlow> results = new HashSet<>();
    boolean hasFormat = false;
    Location sink = null;
    Location source = null;
    ArrayList<Location> pathElements = new ArrayList<>();
    String sinkStatement = null;
    String sinkMethod = null;
    int sinkLineNo = -1;
    String sourceStatement = null;
    String sourceMethod = null;
    int sourceLineNo = -1;
    String pathStatement = null;
    String pathMethod = null;
    int pathLineNo = -1;
    String ID = "";
    while (reader.hasNext()) {
      reader.next();
      if (reader.hasName()) {
        if (reader.getLocalName().equals(Tags.root) && reader.isStartElement()) {
          hasFormat = true;
        } else if (reader.getLocalName().equals(Tags.sink) && reader.isStartElement()) {
          sinkStatement = getAttributeByName(reader, Attributes.statement);
          String re = getAttributeByName(reader, Attributes.linenumber);
          if (!re.equals("")) {
            sinkLineNo = Integer.parseInt(re);
          } else {
            sinkLineNo = -1;
          }
          sinkMethod = getAttributeByName(reader, Attributes.method);
        } else if (reader.getLocalName().equals(Tags.source) && reader.isStartElement()) {
          sourceStatement = getAttributeByName(reader, Attributes.statement);
          ID = getAttributeByName(reader, Attributes.id);
          String re = getAttributeByName(reader, Attributes.linenumber);
          if (!re.equals("")) {
            sourceLineNo = Integer.parseInt(getAttributeByName(reader, Attributes.linenumber));
          } else {
            sourceLineNo = -1;
          }
          sourceMethod = getAttributeByName(reader, Attributes.method);
        } else if (reader.getLocalName().equals(Tags.pathElement) && reader.isStartElement()) {
          pathStatement = getAttributeByName(reader, Attributes.statement);
          String re = getAttributeByName(reader, Attributes.linenumber);
          if (!re.equals("")) {
            pathLineNo = Integer.parseInt(getAttributeByName(reader, Attributes.linenumber));
          } else {
            pathLineNo = -1;
          }
          pathMethod = getAttributeByName(reader, Attributes.method);
        } else if (reader.isEndElement()) {
          if (reader.getLocalName().equals(Tags.sink)) {
            sink = new Location(sinkMethod, sinkStatement, sinkLineNo, null);
          } else if (reader.getLocalName().equals(Tags.source)) {
            source = new Location(sourceMethod, sourceStatement, sourceLineNo, ID);
            results.add(new TaintFlow(source, sink, pathElements));
            pathElements = new ArrayList<>();
          } else if (reader.getLocalName().equals(Tags.pathElement)) {
            Location pathElement = new Location(pathMethod, pathStatement, pathLineNo, null);
            pathElements.add(pathElement);
          }
        }
      }
    }
    if (!hasFormat) return null;
    return results;
  }

  public static Map<String, TreeMap<Integer, Pair<Reference, Reference>>> convertToAQL(
      Set<TaintFlow> flows) {
    Map<String, TreeMap<Integer, Pair<Reference, Reference>>> res = new HashMap<>();
    int i = 1;
    for (TaintFlow flow : flows) {
      ArrayList<Location> all = new ArrayList<>();
      all.add(flow.getSource());
      ArrayList<Location> inter = flow.getIntermediate();
      if (inter.size() >= 2) {
        List<Location> path = inter.subList(1, inter.size() - 1);
        all.addAll(path);
      }
      all.add(flow.getSink());
      String id = null;
      if (!flow.getSource().getID().isEmpty()) {
        id = flow.getSource().getID();
      } else {
        id = i + "";
      }
      if (!res.containsKey(id)) res.put(id, new TreeMap<>());
      for (int j = 0; j < all.size() - 1; j++) {
        Location f = all.get(j);
        Statement sf = new Statement();
        Reference from = new Reference();
        from.setType("from");
        from.setClassname(f.getClassSignature());
        from.setMethod(f.getMethodSignature());
        sf.setLinenumber(f.getLinenumber());
        sf.setStatementfull(f.getStatement());
        String s = f.getStatement();
        if (s.contains("<init>")) {
          s = s.replace("<init>", "[init]");
        }
        if (s.contains("<clinit>")) {
          s = s.replace("<clinit>", "[clinit]");
        }
        if (s.contains("<") && s.contains(">")) {
          s = s.substring(s.indexOf("<") + 1);
          s = s.substring(0, s.indexOf(">"));
        }
        s = s.replace("[init]", "<init>");
        s = s.replace("[clinit]", "<clinit>");
        sf.setStatementgeneric(s);
        from.setStatement(sf);

        Location t = all.get(j + 1);

        Statement st = new Statement();
        Reference to = new Reference();
        to.setType("to");
        to.setClassname(t.getClassSignature());
        to.setMethod(t.getMethodSignature());
        st.setLinenumber(t.getLinenumber());
        st.setStatementfull(t.getStatement());
        s = t.getStatement();
        if (s.contains("<init>")) {
          s = s.replace("<init>", "[init]");
        }
        if (s.contains("<clinit>")) {
          s = s.replace("<clinit>", "[clinit]");
        }
        if (s.contains("<") && s.contains(">")) {
          s = s.substring(s.indexOf("<") + 1);
          s = s.substring(0, s.indexOf(">"));
        }
        s = s.replace("[init]", "<init>");
        s = s.replace("[clinit]", "<clinit>");
        st.setStatementgeneric(s);
        to.setStatement(st);
        Pair<Reference, Reference> step = Pair.make(from, to);
        res.get(id).put(j, step);
      }
      i++;
    }
    return res;
  }

  public static String getAttributeByName(XMLStreamReader reader, String id) {
    for (int i = 0; i < reader.getAttributeCount(); i++)
      if (reader.getAttributeLocalName(i).equals(id)) return reader.getAttributeValue(i);
    return "";
  }
}
