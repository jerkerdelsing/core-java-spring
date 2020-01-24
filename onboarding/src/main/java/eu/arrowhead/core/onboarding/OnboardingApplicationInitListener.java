package eu.arrowhead.core.onboarding;

import eu.arrowhead.common.ApplicationInitListener;
import eu.arrowhead.common.CommonConstants;
import eu.arrowhead.common.CoreDefaults;
import eu.arrowhead.common.database.entity.System;
import eu.arrowhead.common.database.service.CommonDBService;
import eu.arrowhead.common.exception.ArrowheadException;
import eu.arrowhead.common.exception.DataNotFoundException;
import eu.arrowhead.core.onboarding.database.service.OnboardingDBService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class OnboardingApplicationInitListener extends ApplicationInitListener
{

    //=================================================================================================
    // members

    private final CommonDBService commonDBService;
    private final OnboardingDBService onboardingControllerDBService;

    @Autowired
	public OnboardingApplicationInitListener(final CommonDBService commonDBService,
                                             final OnboardingDBService onboardingControllerDBService)
	{
		this.commonDBService = commonDBService;
		this.onboardingControllerDBService = onboardingControllerDBService;
	}

	//=================================================================================================
    // assistant methods

    //-------------------------------------------------------------------------------------------------
    @Override
    protected void customInit(final ContextRefreshedEvent event)
    {
        logger.debug("customInit started...");
        if (!isOwnCloudRegistered())
        {
            registerOwnCloud(event.getApplicationContext());
        }

        try
        {
            final String name = coreSystemRegistrationProperties.getCoreSystem().name().toLowerCase();
            final List<System> oldSystems = onboardingControllerDBService.getSystemByName(name);
            if (!oldSystems.isEmpty())
            {
                for (final System system : oldSystems)
                {
                    onboardingControllerDBService.removeSystemById(system.getId());
                }
            }

            final String authInfo = sslProperties.isSslEnabled() ? Base64.getEncoder().encodeToString(publicKey.getEncoded()) : null;
            onboardingControllerDBService.createSystem(name,
                                                       coreSystemRegistrationProperties.getCoreSystemDomainName(),
                                                       coreSystemRegistrationProperties.getCoreSystemDomainPort(),
                                                       authInfo);
        }
        catch (final ArrowheadException ex)
        {
            logger.error("Can't register {} as a system.", coreSystemRegistrationProperties.getCoreSystem().name());
            logger.debug("Stacktrace", ex);
        }
    }

    //-------------------------------------------------------------------------------------------------
    private boolean isOwnCloudRegistered()
    {
        logger.debug("isOwnCloudRegistered started...");
        try
        {
            commonDBService.getOwnCloud(sslProperties.isSslEnabled());
            return true;
        }
        catch (final DataNotFoundException ex)
        {
            return false;
        }
    }

    //-------------------------------------------------------------------------------------------------
    private void registerOwnCloud(final ApplicationContext appContext)
    {
        logger.debug("registerOwnCloud started...");

        if (!standaloneMode)
        {
            String name = CoreDefaults.DEFAULT_OWN_CLOUD_NAME;
            String operator = CoreDefaults.DEFAULT_OWN_CLOUD_OPERATOR;

            if (sslProperties.isSslEnabled())
            {
                @SuppressWarnings("unchecked") final Map<String, Object> context = appContext.getBean(CommonConstants.ARROWHEAD_CONTEXT, Map.class);
                final String serverCN = (String) context.get(CommonConstants.SERVER_COMMON_NAME);
                final String[] serverFields = serverCN.split("\\.");
                name = serverFields[1];
                operator = serverFields[2];
            }

            commonDBService.insertOwnCloud(operator, name, sslProperties.isSslEnabled(), null);
            logger.info("{}.{} own cloud is registered in {} mode.", name, operator, getModeString());
        }
    }
}