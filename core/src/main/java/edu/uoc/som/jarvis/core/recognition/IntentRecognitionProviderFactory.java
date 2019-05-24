package edu.uoc.som.jarvis.core.recognition;

import edu.uoc.som.jarvis.core.JarvisCore;
import edu.uoc.som.jarvis.core.recognition.dialogflow.DialogFlowApi;
import edu.uoc.som.jarvis.core.session.JarvisSession;
import org.apache.commons.configuration2.Configuration;

import javax.annotation.Nullable;

import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;

/**
 * Builds {@link IntentRecognitionProvider}s from the provided {@code configuration}.
 * <p>
 * This factory inspects the provided {@code configuration} and finds the concrete {@link IntentRecognitionProvider}
 * to construct. If the provided {@code configuration} does not define any {@link IntentRecognitionProvider}, a
 * {@link DefaultIntentRecognitionProvider} is returned, providing minimal support to
 * {@link JarvisSession} management.
 * <p>
 * <b>Note:</b> {@link DefaultIntentRecognitionProvider} does not handle
 * {@link edu.uoc.som.jarvis.intent.IntentDefinition} and {@link edu.uoc.som.jarvis.intent.RecognizedIntent}
 * computation.
 * If the bot application requires such features a valid {@link IntentRecognitionProvider} must be specified in the
 * provided configuration.
 *
 * @see IntentRecognitionProvider
 * @see DefaultIntentRecognitionProvider
 */
public class IntentRecognitionProviderFactory {

    /**
     * The {@link Configuration} key used to specify whether to enable intent recognition monitoring.
     * <p>
     * Intent recognition monitoring is enabled by default, and stores the results in the {@code data/analytics}
     * folder. It can be disabled by explicitly setting this property to {@code false} in the {@link Configuration}
     * file.
     */
    public static String ENABLE_RECOGNITION_ANALYTICS = "jarvis.recognition.enable_monitoring";

    /**
     * Returns the {@link IntentRecognitionProvider} matching the provided {@code configuration}.
     * <p>
     * If the provided {@code configuration} does not define any {@link IntentRecognitionProvider}, a
     * {@link DefaultIntentRecognitionProvider} is returned, providing minimal support to
     * {@link JarvisSession} management.
     * <p>
     * The created {@link IntentRecognitionProvider} embeds a {@link RecognitionMonitor} that logs monitoring
     * information regarding the intent recognition. The {@link RecognitionMonitor} can be disabled by setting the
     * {@link #ENABLE_RECOGNITION_ANALYTICS} property to {@code false} in the configuration.
     *
     * @param jarvisCore    the {@link JarvisCore} instance to build the {@link IntentRecognitionProvider} from
     * @param configuration the {@link Configuration} used to define the {@link IntentRecognitionProvider} to build
     * @return the {@link IntentRecognitionProvider} matching the provided {@code configuration}
     * @see #getRecognitionMonitor(JarvisCore, Configuration)
     */
    public static IntentRecognitionProvider getIntentRecognitionProvider(JarvisCore jarvisCore, Configuration
            configuration) {
        checkNotNull(jarvisCore, "Cannot get an %s from the provided %s %s", IntentRecognitionProvider.class
                .getSimpleName(), JarvisCore.class.getSimpleName(), jarvisCore);
        checkNotNull(configuration, "Cannot get an %s the provided %s %s", IntentRecognitionProvider.class
                .getSimpleName(), Configuration.class.getSimpleName(), configuration);
        RecognitionMonitor recognitionMonitor = getRecognitionMonitor(jarvisCore, configuration);
        if (configuration.containsKey(DialogFlowApi.PROJECT_ID_KEY)) {
            /*
             * The provided configuration contains DialogFlow-related information.
             */
            return new DialogFlowApi(jarvisCore, configuration, recognitionMonitor);
        } else {
            /*
             * The provided configuration does not contain any IntentRecognitionProvider information, returning a
             * DefaultIntentRecognitionProvider.
             */
            return new DefaultIntentRecognitionProvider(configuration, recognitionMonitor);
        }
    }

    /**
     * Retrieves and creates the {@link RecognitionMonitor} from the provided {@link Configuration}.
     *
     * @param jarvisCore    the {@link JarvisCore} used to initialize the {@link RecognitionMonitor}
     * @param configuration the {@link Configuration} used to initialize the {@link RecognitionMonitor}
     * @return the created {@link RecognitionMonitor}, or {@code null} intent recognition monitoring is disabled in
     * the provided {@link Configuration}
     * @see #ENABLE_RECOGNITION_ANALYTICS
     */
    @Nullable  private static RecognitionMonitor getRecognitionMonitor(JarvisCore jarvisCore,
                                                            Configuration configuration) {
        boolean enableRecognitionAnalytics = configuration.getBoolean(ENABLE_RECOGNITION_ANALYTICS, true);
        RecognitionMonitor monitor = null;
        if (enableRecognitionAnalytics) {
            monitor = new RecognitionMonitor(jarvisCore.getJarvisServer());
        }
        return monitor;
    }
}
