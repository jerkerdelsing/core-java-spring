package eu.arrowhead.core.translator.services.fiware;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import eu.arrowhead.common.CoreCommonConstants;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.exception.DataNotFoundException;
import eu.arrowhead.common.http.HttpService;
import eu.arrowhead.common.CoreSystemRegistrationProperties;
import eu.arrowhead.common.SSLProperties;
import eu.arrowhead.core.translator.services.fiware.common.FiwareEntity;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@Service
public class FiwareService {

    //=================================================================================================
    // members
    private final Logger logger = LogManager.getLogger(FiwareService.class);
    private final Map<String, FiwareEntity> entities = new HashMap<>();
    private FiwareDriver fiwareDriver;
    private ArrowheadDriver arrowheadDriver;

    @Value(CoreCommonConstants.$FIWARE_SERVER_HOST)
    private String fiwareHost;

    @Value(CoreCommonConstants.$FIWARE_SERVER_PORT)
    private int fiwarePort;
    
    @Value(CoreCommonConstants.$SERVER_PORT)
    private int translatorPort;

    @Autowired
    private HttpService httpService;

    @Autowired
    private SSLProperties sslProperties;
    
    @Autowired
    private CoreSystemRegistrationProperties coreSystemRegistrationProperties;

    private final ObjectMapper mapper = new ObjectMapper();

    private final String APPLICATION_SENML = "application/senml+json";
    private final String SENML_NAME = "n";
    private final String SENML_VALUE = "v";
    private final String FIWARE_VALUE = "value";
    private final String FIWARE_METADATA = "metadata";
    private final String FIWARE_EMPTY = "";

    //=================================================================================================
    // methods
    //-------------------------------------------------------------------------------------------------
    public void start() {
        logger.info("-- Starting FIWARE Service --");
        logger.info("Broker host: [{}] port: [{}]", fiwareHost, fiwarePort);
        logger.info("-----------------------------");
    }
    
    
    //-------------------------------------------------------------------------------------------------
    public void unregisterAll() {
        logger.info("-- Custom destroy of FIWARE Service --");
        for (Map.Entry<String,FiwareEntity> entry: entities.entrySet()) {
            getArrowheadDriver().serviceRegistryUnegisterAllServices(entry.getValue());
        }
        logger.info("-----------------------------");
    }

    //-------------------------------------------------------------------------------------------------
    public FiwareEntity[] listEntities(Map<String, Object> queryParams) {
        return getFiwareDriver().queryEntitiesList(queryParams);
    }

    //-------------------------------------------------------------------------------------------------
    public int createEntity(Map<String, Object> queryParams, FiwareEntity entity) {
        return getFiwareDriver().createEntity(queryParams, entity);
    }

    //-------------------------------------------------------------------------------------------------
    public FiwareEntity queryEntity(String entityId, Map<String, Object> queryParams) {
        return getFiwareDriver().queryEntity(entityId, queryParams);
    }

    //-------------------------------------------------------------------------------------------------
    public Object retrieveEntityAttributes(String entityId, Map<String, Object> queryParams) {
        return getFiwareDriver().retrieveEntityAttributes(entityId, queryParams);
    }

    //-------------------------------------------------------------------------------------------------
    public int updateOrAppendEntityAttributes(String entityId, Map<String, Object> queryParams, Object attributes) {
        return getFiwareDriver().updateOrAppendEntityAttributes(entityId, queryParams, attributes);
    }

    //-------------------------------------------------------------------------------------------------
    public int removeEntity(String entityId, Map<String, Object> queryParams) {
        return getFiwareDriver().removeEntity(entityId, queryParams);
    }

    //-------------------------------------------------------------------------------------------------
    public Object[] queryTypesList(Map<String, Object> queryParams) {
        return getFiwareDriver().queryTypesList(queryParams);
    }

    //-------------------------------------------------------------------------------------------------
    public Object retrieveEntityType(String entityType) {
        return getFiwareDriver().retrieveEntityType(entityType);
    }

    //-------------------------------------------------------------------------------------------------
    public Object pluginEntityService(String entityId, String serviceName, String contentType) {
        FiwareEntity entity = entities.get(entityId);
        if (entity == null) {
            throw new DataNotFoundException(String.format("No entity with id:%s", entityId), HttpStatus.NOT_FOUND.value());
        }

        Object value = entity.getProperty(serviceName);
        if (value == null) {
            throw new DataNotFoundException(String.format("No entity with id:%s and service:%s", entityId, serviceName), HttpStatus.NOT_FOUND.value());
        }

        switch (contentType) {
            case MediaType.APPLICATION_JSON_VALUE:
                return value;

            case APPLICATION_SENML:
                return jsonObjectToSenMl(serviceName, value);

            case MediaType.TEXT_PLAIN_VALUE:
                return jsonObjectToText(serviceName, value);

            default:
                throw new ArrowheadException(String.format("Wrong Content Type:%s", contentType), HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        }
    }

    //=================================================================================================
    // assistant methods
    //-------------------------------------------------------------------------------------------------
    private FiwareDriver getFiwareDriver() {
        if (fiwareDriver == null) {
            try {
                fiwareDriver = new FiwareDriver(httpService, fiwareHost, fiwarePort);
            } catch(Exception ex) {
                logger.warn("Exception: "+ex.getLocalizedMessage());
            }
        }
        return fiwareDriver;
    }
    
    private ArrowheadDriver getArrowheadDriver() {
        if (arrowheadDriver == null) {
            try {
                arrowheadDriver = new ArrowheadDriver(translatorPort, httpService, sslProperties, coreSystemRegistrationProperties);
            } catch(Exception ex) {
                logger.warn("{} {} {} {}", translatorPort, httpService == null, sslProperties == null, coreSystemRegistrationProperties == null);
            }
            arrowheadDriver = getArrowheadDriver();
        }
        return arrowheadDriver;
    }
    

    //-------------------------------------------------------------------------------------------------
    private void updateEntities(FiwareEntity[] updatedEntitiesArray) {
        Map<String, FiwareEntity> updatedEntities = new HashMap<>();

        for (FiwareEntity entity : updatedEntitiesArray) {
            updatedEntities.put(entity.getId(), entity);
        }

        Set<String> checkIfUpdated = new HashSet<>(entities.keySet());
        checkIfUpdated.retainAll(updatedEntities.keySet());

        checkIfUpdated.forEach((id) -> {
            if (entities.get(id).equals(updatedEntities.get(id))) {
                logger.debug("SAME entity id:{} type:{} -> Nothing", id, entities.get(id).getType());
            } else {
                logger.debug("UPDATED entity id:{} type:{} -> Save changes", id, entities.get(id).getType());
                entities.replace(id, updatedEntities.get(id));
            }
        });

        Set<String> checkIfNew = new HashSet<>(updatedEntities.keySet());
        checkIfNew.removeAll(entities.keySet());

        checkIfNew.forEach((id) -> {
            logger.debug("NEW entity id:{} type:{} -> AH register", id, updatedEntities.get(id).getType());
            entities.put(id, updatedEntities.get(id));
            getArrowheadDriver().serviceRegistryRegisterAllServices(updatedEntities.get(id));
        });

        Set<String> checkIfRemoved = new HashSet<>(entities.keySet());
        checkIfRemoved.removeAll(updatedEntities.keySet());

        checkIfRemoved.forEach((id) -> {
            logger.debug("REMOVE entity id:{} type:{} -> AH unregister", id, entities.get(id).getType());
            FiwareEntity entity = entities.remove(id);
            getArrowheadDriver().serviceRegistryUnegisterAllServices(entity);
        });

    }

    //-------------------------------------------------------------------------------------------------
    private ArrayNode jsonObjectToSenMl(String serviceName, Object o) {
        ArrayNode senML = mapper.createArrayNode();
        JsonNode json = mapper.valueToTree(o);
        JsonNode sensorValue = mapper.createObjectNode();
        ((ObjectNode) sensorValue).put(SENML_NAME, serviceName);
        ((ObjectNode) sensorValue).put(SENML_VALUE, json.has(FIWARE_VALUE)?json.get(FIWARE_VALUE).asText():FIWARE_EMPTY);
        JsonNode metadataValue = mapper.createObjectNode();
        ((ObjectNode) metadataValue).put(SENML_NAME, FIWARE_METADATA);
        ((ObjectNode) metadataValue).put(SENML_VALUE, json.has(FIWARE_METADATA)?json.get(FIWARE_METADATA).asText():FIWARE_EMPTY);
        senML.addAll(Arrays.asList(sensorValue, metadataValue));
        return senML;
    }

    //-------------------------------------------------------------------------------------------------
    private String jsonObjectToText(String serviceName, Object o) {
        JsonNode json = mapper.valueToTree(o);
        return String.format("%s value is %s", serviceName, json.has(FIWARE_VALUE)?json.get(FIWARE_VALUE).asText():"unknown");
    }

    //=================================================================================================
    // Scheduled methods
    //-------------------------------------------------------------------------------------------------
    @Scheduled(fixedRate = 1000, initialDelay = 10000)
    private void autoBrokerSynchronization() {
        logger.debug("=========== [Synchronization] ===========");
        updateEntities(getFiwareDriver().queryEntitiesList());
        logger.debug("=========================================");
    }

}
