package org.v8LogScanner.logsCfg;

import org.v8LogScanner.commonly.fsys;
import org.v8LogScanner.logs.LogsOperations;
import org.v8LogScanner.rgx.RegExp;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>Create a reactive object representation of the logcfg.xml used by 1c enterprise platform</p>
 *
 * <p>Note that LogBuilder requires installed 1c enterpise in order to read and write existing logcxfg.xml files
 *
 * @author R.I.P.real
 * @see <a href="https://github.com/ripreal/>repository with examples</a>
 */
public class LogBuilder {
    /**
     * It maintains structured <events> block of a cfg file
     */
    private List<LogEvent> events = new ArrayList<>();

    /**
     * It maintains structured <log> block of a cfg file
     */
    private List<LogLocation> locations = new ArrayList<>();

    /**
     * It maintains structured <property> block of a cfg file
     */
    private List<LogProperty> properties = new ArrayList<>();

    /**
     * It maintains structured <dump> tag of a cfg file
     */
    private LogDump dumps = new LogDump();

    /**
     * The file paths which logcfg.xml file save to
     */
    private List<Path> cfgPaths = null;

    /**
     * It keeps <plansql> tag
     */
    private boolean allowPLanSql = false;

    /**
     * Stores reactive xml-DOM representaion of a LOgBuilder object model
     */
    private String content = "";

    /**
     * If this constructor is used then logcfg.xml paths
     * will be taken from default 1c enterpise catalogs
     */
    public LogBuilder() {
        cfgPaths = LogsOperations.scanCfgPaths();
    }

    /**
     * Create new builder from desired logcfg.xml paths. Default 1c enterprise paths will be ignored
     * @param cfgPaths
     */
    public LogBuilder(List<Path> cfgPaths) {
        this.cfgPaths = cfgPaths;
    }

    /**
     * Construct a logcfg.xml file from the object model and save it to specified
     * location (put in the conctructor or a setter)
     *
     * @return written file by the specified location
     */
    public File writeToXmlFile() {
        File logFile = null;
        try {
            DOMSource source = buildXmlDoc();

            for (Path path : cfgPaths) {
                logFile = new File(path.toUri());
                Transformer transformer = initTransformer();
                StreamResult streamresult = new StreamResult(logFile);
                transformer.transform(source, streamresult);
                removeNodeElement(logFile, ".*dummy.*");
            };
        } catch (ParserConfigurationException | TransformerException | IOException  e) {
            e.printStackTrace();
        }

        return logFile;
    }

    /**
     * Parse file located by cfg path specified as costrunctor parameter or in setter
     * @return itself
     */
    public LogBuilder readCfgFile(){

        clearAll();

        for (Path path : cfgPaths) {
            File logFile = new File(path.toUri());

            if (!logFile.exists())
                throw new RuntimeException("file does not exists!");

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            try {
                Document doc = docFactory.newDocumentBuilder().parse(logFile);

                if (!doc.getDocumentElement().getTagName().matches(LogConfig.CONFIG_TAG_NAME))
                    throw new CfgParsingError(String.format(
                            "Wrong file format. Root element must start with <%s> tag", LogConfig.CONFIG_TAG_NAME));

                parseLogCfg(doc.getFirstChild().getChildNodes());
            }
            catch (CfgParsingError | SAXException | ParserConfigurationException | IOException e) {
                e.printStackTrace();
            }
        }
        writeToItself();

        return this;
    }

    /**
     * Contruct log file for analyzing all events logged by 1c
     * @return prepared but not written text file with settings
     */
    public LogBuilder buildAllEvents() {
        return this
            .addLocLocation()
            .addLogProperty()
            .addEvent(LogEvent.LogEventComparisons.ne, RegExp.PropTypes.Event, "");
    }

    /**
     * Construct log file for analyzing dbmsql, dbpostgrs, db2, dboracle database events.
     * To analyse deadlocks and other sql errors additional excp event is added.
     * Note you need to add the location tag in on order to get *.log file working.
     * @return prepared but not written text file with settings.
     */
    public LogBuilder buildSQlEvents() {
        return this
            .addLocLocation()
            .addLogProperty()
            .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "dbmssql")
            .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "dbpostgrs")
            .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "db2")
            .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "dboracle")
            .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "excp");
    }

    /**
     * Construct the *.log file for analyzing excp events
     * Note you need to add the location tag in on order to get *.log file working.
     * @return prepared but not written text file with settings
     */
    public LogBuilder buildExcpEvents() {
        return this
            .addLocLocation()
            .addLogProperty()
            .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "excp");
    }

    /**
     * Construct the *.log file for analyzing long any events
     * Note you need to add the location tag in on order to get *.log file working.
     * @return prepared but not written text file with settings
     */
    public LogBuilder buildLongEvents() {

        LogEvent longEv = new LogEvent();

        longEv.setProp(RegExp.PropTypes.Event);
        longEv.setVal(RegExp.PropTypes.Event, "");
        longEv.setComparison(RegExp.PropTypes.Event, LogEvent.LogEventComparisons.ne);

        longEv.setProp(RegExp.PropTypes.Duration);
        longEv.setVal(RegExp.PropTypes.Duration, "20000000");
        longEv.setComparison(RegExp.PropTypes.Duration, LogEvent.LogEventComparisons.ge);

        return this
            .addLocLocation()
            .addLogProperty()
            .addEvent(longEv);
    }

    /**
     * Construct the *.log file for analyzing managed locks put by 1c Enterprise
     * Note you need to add the location tag in on order to get *.log file working.
     * @return prepared but not written text file with settings
     */
    public LogBuilder build1cLocksEvents(){
        return this
            .addLocLocation()
            .addLogProperty()
            .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, RegExp.EventTypes.TLOCK.toString())
            .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, RegExp.EventTypes.TTIMEOUT.toString())
            .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, RegExp.EventTypes.TDEADLOCK.toString());
    }

    /**
     * Construct the *.log file for analyzing all DB MSQL database queries with the filter by user and infobase.
     * Note sql plan also included.
     * Note you need to add the location tag in on order to get *.log file working.
     * @param user - user descr field in 1c enterpise
     * @param infobase_name name of base in conection string
     * @return prepared but not written text file with settings
     */
    public LogBuilder buildInvestigateNonEffectiveQueries(String user, String infobase_name){

        LogEvent event = new LogEvent();

        event.setProp(RegExp.PropTypes.Event);
        event.setVal(RegExp.PropTypes.Event, RegExp.EventTypes.DBMSSQL.toString());
        event.setComparison(RegExp.PropTypes.Event, LogEvent.LogEventComparisons.eq);

        event.setProp(RegExp.PropTypes.ProcessName);
        event.setVal(RegExp.PropTypes.ProcessName, "%" + infobase_name + "%");
        event.setComparison(RegExp.PropTypes.ProcessName, LogEvent.LogEventComparisons.like);

        event.setProp(RegExp.PropTypes.Usr);
        event.setVal(RegExp.PropTypes.Usr, user);
        event.setComparison(RegExp.PropTypes.Usr, LogEvent.LogEventComparisons.eq);

        return this
            .addLocLocation()
            .addLogProperty()
            .addEvent(event)
            .setPlanSql(true);
    }

    /**
     * Construct the *.log file for analyzing excp, conn, proc, admin, sesn, clstr events.
     * Note you need to add the location tag in on order to get *.log file working.
     * @return prepared but not written text file with settings
     */
    public LogBuilder buildEverydayEvents() {
        return this
        .addLocLocation()
        .addLogProperty()
        .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "excp")
        .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "conn")
        .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "PROC")
        .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "ADMIN")
        .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "SESN")
        .addEvent(LogEvent.LogEventComparisons.eq, RegExp.PropTypes.Event, "CLSTR");
    }

    /**
     * add user defined log location inside <log> tag. Path is not checked.
     * @param location - folder that is used to create logs
     * @param history - amount of hours in which file will be stored on a disk.
     * @return itself
     */
    public LogBuilder addLocLocation(String location, String history) {
        locations.add(new LogLocation(location, history));
        return this.addLogProperty();
    }

    /**
     * Add default cfg location and also new <log> tag which is located </>on c:\\v8logs\. Path allowance is not checked.
     * @return itself
     */
    public LogBuilder addLocLocation() {
        if (locations.size() == 0)
            locations.add(new LogLocation());
        return this;
    }

    /**
     * add new <event> tag to the <log> with attributes defined by user.
     * @param comparison
     * @param event
     * @param val
     * @return
     */
    public LogBuilder addEvent(LogEvent.LogEventComparisons comparison, RegExp.PropTypes event, String  val) {
        events.add(new LogEvent(comparison, event, val));
        return this.addLocLocation().addLogProperty();
    }

    /**
     *
     * @param event
     * @return
     */

    public LogBuilder addEvent(LogEvent event) {
        events.add(event);
        return this.addLocLocation().addLogProperty();
    }

    public LogBuilder addEvent() {
        events.add(new LogEvent(LogEvent.LogEventComparisons.ne, RegExp.PropTypes.Event, ""));
        return this.addLocLocation().addLogProperty();
    }

    public void removeLogEvent(LogEvent event) {
        events.remove(event);
    }

    public LogBuilder addLogProperty(String name) {
        LogProperty prop = new LogProperty(name);
        if (!properties.contains(prop))
            properties.add(prop);
        return this.addLocLocation();
    }

    public LogBuilder setCreateDumps(boolean create) {
        dumps.setCreate(create);
        return this;
    }

    public LogBuilder setCreateDumps(String create) {
        setCreateDumps(Boolean.parseBoolean(create));
        return this;
    }

    public LogBuilder addLogProperty() {
        if (properties.size() == 0) {
            properties.add(new LogProperty());
        }
        return this.addLocLocation();
    }

    public List<LogEvent> getLogEvents() {
        return events;
    }

    public LogBuilder setPlanSql(boolean allow) {
        allowPLanSql = allow;
       return this;
    }

    public List<Path> getCfgPaths() {
        return cfgPaths;
    }

    public LogBuilder setCfgPaths(Path cfgPaths) {
        this.cfgPaths.clear();
        this.cfgPaths.add(cfgPaths);
        return this;
    }

    public List<String> getLocations() {
        List<String> res = new ArrayList<>();
        locations.forEach((item) -> res.add(item.getLocation()));
        return res;
    }

    public void clearLocations() {
        locations.clear();
    }

    public void clearAll() {
        events.clear();
        locations.clear();
        properties.clear();
        dumps = new LogDump();
    }

    /**
     * Recieve a actual xml-DOM representation of a object model logcfg.xml
     * @return xml-DOM of a logcfg.xml
     */
    public String getContent() {
        return content;
    }

    // PRIVATE SECTION

    private void writeToItself() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeToStream(baos);
        content = baos.toString();
    }

    /**
     * Construct a text of a logcfg.xml file from the object model and pass it to the stream
     * @param stream - a stream for the output of the logcfg.xml content
     */
    private void writeToStream(OutputStream stream) {
        try {
            Transformer transformer = initTransformer();
            StreamResult streamresult = new StreamResult(stream);
            transformer.transform(buildXmlDoc(), streamresult);
        } catch (TransformerException | ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    private Transformer initTransformer() throws TransformerConfigurationException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        return transformer;
    }

    private DOMSource buildXmlDoc() throws ParserConfigurationException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        Document doc = docFactory.newDocumentBuilder().newDocument();

        // root elements
        Element root = doc.createElement(LogConfig.CONFIG_TAG_NAME);
        root.setAttribute(LogConfig.CONFIG_PROP_NAME, LogConfig.CONFIG_PROP_VAL);
        doc.appendChild(root);

        Element dumpEl = doc.createElement(LogConfig.DUMP_TAG_NAME);
        dumpEl.setAttribute(LogConfig.CREATE_NAME, dumps.getCreate());
        root.appendChild(dumpEl);

        Element logEl = doc.createElement(LogConfig.LOC_TAG_NAME);
        for(LogLocation loc : locations) {

            logEl.setAttribute(LogConfig.LOC_HISTORY_NAME, loc.getHistory());
            logEl.setAttribute(LogConfig.LOC_LOCATION_NAME, loc.getLocation());

            events.forEach((event) -> {
                Element eventEl = doc.createElement(LogConfig.EVENT_TAG_NAME);
                for(LogEvent.EventRow eventRow : event) {
                    Element eventPropEl = doc.createElement(eventRow.getComparison());
                    eventPropEl.setAttribute(LogConfig.PROP_NAME, eventRow.getKey());
                    eventPropEl.setAttribute(LogConfig.VAL_NAME, eventRow.getVal());
                    eventEl.appendChild(eventPropEl);
                };
                logEl.appendChild(eventEl);
            });

            properties.forEach((prop) -> {
                Element propEl = doc.createElement(LogConfig.PROP_NAME);
                propEl.setAttribute(LogConfig.PROPERTY_PROP_NAME, prop.getName());

                Element dummyEl = doc.createElement("dummy");
                propEl.appendChild(dummyEl);

                logEl.appendChild(propEl);
            });

            root.appendChild(logEl);
        }

        if (allowPLanSql) {
            Element planEl = doc.createElement(LogConfig.PLAN__SQL_TAG_NAME);
            root.appendChild(planEl);
        }

        return new DOMSource(doc);
    }

    private void removeNodeElement(File logFile, String pattern) throws IOException {

        List<String> strings = fsys.readAllFromFileToList(logFile.getAbsolutePath());

        List<String> processedStrings =
        strings
            .stream()
            .filter((el) -> !el.matches(pattern))
            .collect(Collectors.toList());

        fsys.writeInNewFile(processedStrings, logFile);
    }

    private void parseLogCfg(NodeList nodeList) throws CfgParsingError {

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);

            String nodeName = node.getNodeName();
            if (nodeName.matches("(?i)" + LogConfig.DUMP_TAG_NAME)) {
                setCreateDumps(node.getAttributes().getNamedItem(LogConfig.CREATE_NAME).getNodeValue());
            } else if (nodeName.matches("(?i)" + LogConfig.LOC_TAG_NAME)) {
                NamedNodeMap attributes = node.getAttributes();
                addLocLocation(
                    attributes.getNamedItem(LogConfig.LOC_LOCATION_NAME).getNodeValue().toString(),
                    attributes.getNamedItem(LogConfig.LOC_HISTORY_NAME).getNodeValue().toString()
                );
                parseLogCfg(node.getChildNodes());

            } else if (nodeName.matches("(?i)" + LogConfig.EVENT_TAG_NAME)) {
                NodeList propList = node.getChildNodes();
                LogEvent event = new LogEvent();
                for (int j = 0; j < propList.getLength(); j++) {
                    Node propNode = propList.item(j);
                    NamedNodeMap attributes = propNode.getAttributes();
                    if (attributes  != null) { // tag with non emty attribute means prop tag found
                        try {
                            RegExp.PropTypes propName = LogEvent.parseProp(attributes.getNamedItem(LogConfig.PROP_NAME).getNodeValue());
                            event.setProp(propName);
                            event.setVal(propName, attributes.getNamedItem(LogConfig.VAL_NAME).getNodeValue());
                            event.setComparison(propName, propNode.getNodeName());
                        }
                        catch(RuntimeException e){
                            throw new CfgParsingError("Error. Cannont parse one or more <event> attributes. Non-parseable attributes set by default"
                            +"\njava exception: " + e.getMessage());
                        }
                    }
                }
                events.add(event);
            } else if (nodeName.matches("(?i)" + LogConfig.PROPERTY_TAG_NAME)) {
                addLogProperty(node.getAttributes().getNamedItem(LogConfig.PROPERTY_PROP_NAME).getNodeValue());
            }
        }
    }

}