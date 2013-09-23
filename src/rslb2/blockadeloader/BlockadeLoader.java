/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rslb2.blockadeloader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import rescuecore2.log.Logger;
import rescuecore2.messages.control.KSCommands;
import rescuecore2.standard.components.StandardSimulator;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

/**
 *
 * @author Marc Pujol <mpujol@iiia.csic.es>
 */
public class BlockadeLoader extends StandardSimulator {

    public final String KEY_SCENARIO = "gis.map.scenario";

    public BlockadeLoader() {}

    @Override
    public void postConnect() {
        super.postConnect();
        Logger.info("connected");
    }

    @Override
    protected void processCommands(KSCommands c, ChangeSet changes) {
        long start = System.currentTimeMillis();
        int time = c.getTime();
        Logger.info("Timestep " + time);

        if (time == 1) {
            deployBlockades(changes);
        }

        long end = System.currentTimeMillis();
        Logger.info("Timestep " + time + " took " + (end - start) + " ms");
    }

    private void deployBlockades(ChangeSet changes) {
        Logger.warn("Scenario: " + config.getValue(KEY_SCENARIO));
        Document scenario = loadDocument();

        ArrayList<BlockadeData> blockades = new ArrayList<>();
        for (Object node : scenario.selectNodes("//scenario:scenario/scenario:blockade")) {
            if (node instanceof Element) {
                blockades.add(new BlockadeData((Element)node));
            }
        }

        try {
            List<EntityID> idList = requestNewEntityIDs(blockades.size());
            Iterator<EntityID> ids = idList.iterator();
            for (BlockadeData bd : blockades) {
                createBlockade(changes, bd, ids.next());
            }
        } catch (InterruptedException ex) {
            Logger.fatal(ex.getLocalizedMessage(), ex);
        }
    }

    private void createBlockade(ChangeSet changes, BlockadeData bd, EntityID id) {
        StandardEntity entity = model.getEntity(bd.location);
        if (entity == null) {
            Logger.fatal("Error loading blockade for road " + bd.location + ". This doesn't seem to be a road.");
            return;
        }
        if (!(entity instanceof Road)) {
            Logger.fatal("Error loading blockades: location " + bd.location + " is not a road.");
            return;
        }

        Road road = (Road)entity;

        // Instantiate the blockade
        Blockade blockade = new Blockade(id);
        blockade.setApexes(road.getApexList());
        blockade.setX(road.getX());
        blockade.setY(road.getY());
        blockade.setRepairCost(bd.cost);
        blockade.setPosition(road.getID());

        // Report the addition to the kernel
        List<EntityID> roadBlockades = road.getBlockades();
        if (roadBlockades == null) {
            roadBlockades = new ArrayList<>();
        }
        roadBlockades.add(blockade.getID());
        road.setBlockades(roadBlockades);
        changes.addChange(road, road.getBlockadesProperty());
        changes.addAll(Arrays.asList(new Blockade[]{blockade}));
    }

    private Document loadDocument() {
        SAXReader reader = new SAXReader();
        try {
            Document document = reader.read(getScenarioPath());
            return document;
        } catch (DocumentException ex) {
            Logger.fatal(ex.getLocalizedMessage(), ex);
        }
        return null;
    }

    private String getScenarioPath() {
        // We need to remove the two first back-directories if its relative
        String path = config.getValue(KEY_SCENARIO);
        if (path.startsWith(".")) {
            path = path.substring(path.indexOf('/')+1);
            path = path.substring(path.indexOf('/')+1);
        }
        Logger.trace("Loading scenario: " + path);
        return path;
    }

    private class BlockadeData {
        private EntityID location;
        private int cost;

        public BlockadeData(Element node) {
            location = new EntityID(Integer.valueOf(node.attribute("location").getValue()));
            cost = Integer.valueOf(node.attribute("cost").getValue());
        }
    }

}
