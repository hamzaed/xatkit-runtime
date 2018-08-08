package fr.zelus.jarvis.core;

import fr.inria.atlanmod.commons.log.Log;
import fr.zelus.jarvis.core.session.JarvisContext;
import fr.zelus.jarvis.core.session.JarvisSession;
import fr.zelus.jarvis.intent.EventInstance;
import fr.zelus.jarvis.io.EventProvider;
import fr.zelus.jarvis.io.WebhookEventProvider;
import fr.zelus.jarvis.module.Action;
import fr.zelus.jarvis.module.EventProviderDefinition;
import fr.zelus.jarvis.module.Parameter;
import fr.zelus.jarvis.orchestration.*;
import fr.zelus.jarvis.server.JarvisServer;
import fr.zelus.jarvis.util.Loader;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * The concrete implementation of a {@link fr.zelus.jarvis.module.Module} definition.
 * <p>
 * A {@link JarvisModule} manages a set of {@link JarvisAction}s that represent the concrete actions that can
 * be executed by the module. This class provides primitives to enable/disable specific actions, and construct
 * {@link JarvisAction} instances from a given {@link EventInstance}.
 * <p>
 * Note that enabling a {@link JarvisAction} will load the corresponding class, that must be stored in the
 * <i>action</i> package of the concrete {@link JarvisModule} implementation. For example, enabling the action
 * <i>MyAction</i> from the {@link JarvisModule} <i>myModulePackage.MyModule</i> will attempt to load the class
 * <i>myModulePackage.action.MyAction</i>.
 */
public abstract class JarvisModule {

    /**
     * The {@link JarvisCore} instance containing this module.
     */
    protected JarvisCore jarvisCore;

    /**
     * The {@link Configuration} used to initialize this class.
     * <p>
     * This {@link Configuration} is used by the {@link JarvisModule} to initialize the {@link EventProvider}s and
     * {@link JarvisAction}s.
     *
     * @see #startEventProvider(EventProviderDefinition)
     * @see #createJarvisAction(ActionInstance, JarvisSession)
     */
    protected Configuration configuration;

    /**
     * The {@link Map} containing the {@link JarvisAction} associated to this module.
     * <p>
     * This {@link Map} is used as a cache to retrieve {@link JarvisAction} that have been previously loaded.
     *
     * @see #enableAction(Action)
     * @see #disableAction(Action)
     * @see #createJarvisAction(ActionInstance, JarvisSession)
     */
    protected Map<String, Class<? extends JarvisAction>> actionMap;

    /**
     * The {@link Map} containing the {@link EventProviderThread}s associated to this module.
     * <p>
     * This {@link Map} filled when new {@link EventProvider}s are started (see
     * {@link #startEventProvider(EventProviderDefinition)}), and is used to cache
     * {@link EventProviderThread}s and stop them when the module is {@link #shutdown()}.
     *
     * @see #shutdown()
     */
    protected Map<String, EventProviderThread> eventProviderMap;


    /**
     * Constructs a new {@link JarvisModule} from the provided {@link JarvisCore} and {@link Configuration}.
     * <p>
     * <b>Note</b>: this constructor will be called by jarvis internal engine when initializing the
     * {@link JarvisModule}s. Subclasses implementing this constructor typically need additional parameters to be
     * initialized, that can be provided in the {@code configuration}.
     *
     * @param jarvisCore    the {@link JarvisCore} instance associated to this module
     * @param configuration the {@link Configuration} used to initialize the {@link JarvisModule}
     * @throws NullPointerException if the provided {@code jarvisCore} or {@code configuration} is {@code null}
     * @see #JarvisModule(JarvisCore)
     */
    public JarvisModule(JarvisCore jarvisCore, Configuration configuration) {
        checkNotNull(jarvisCore, "Cannot construct a %s from the provided %s %s", this.getClass().getSimpleName(),
                JarvisCore.class.getSimpleName(), jarvisCore);
        checkNotNull(configuration, "Cannot construct a %s from the provided %s %s", this.getClass().getSimpleName(),
                Configuration.class.getSimpleName(), configuration);
        this.jarvisCore = jarvisCore;
        this.configuration = configuration;
        this.actionMap = new HashMap<>();
        this.eventProviderMap = new HashMap<>();
    }

    /**
     * Constructs a new {@link JarvisModule} from the provided {@link JarvisCore}.
     * <p>
     * <b>Note</b>: this constructor should be used by {@link JarvisModule}s that do not require additional
     * parameters to be initialized. In that case see {@link #JarvisModule(JarvisCore, Configuration)}.
     *
     * @throws NullPointerException if the provided {@code jarvisCore} is {@code null}
     * @see #JarvisModule(JarvisCore, Configuration)
     */
    public JarvisModule(JarvisCore jarvisCore) {
        this(jarvisCore, new BaseConfiguration());
    }

    /**
     * Returns the name of the module.
     * <p>
     * This method returns the value of {@link Class#getSimpleName()}, and can not be overridden by concrete
     * subclasses. {@link JarvisModule}'s names are part of jarvis' naming convention, and are used to dynamically
     * load modules and actions.
     *
     * @return the name of the module.
     */
    public final String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Return the {@link JarvisCore} instance associated to this module.
     *
     * @return the {@link JarvisCore} instance associated to this module
     */
    public final JarvisCore getJarvisCore() {
        return this.jarvisCore;
    }

    /**
     * Starts the {@link EventProvider} corresponding to the provided {@code eventProviderDefinition}.
     * <p>
     * This method dynamically loads the {@link EventProvider} corresponding to the provided {@code
     * eventProviderDefinition}, and starts it in a dedicated {@link Thread}.
     * <p>
     * This method also registers {@link WebhookEventProvider}s to the underlying {@link JarvisServer} (see
     * {@link JarvisServer#registerWebhookEventProvider(WebhookEventProvider)}).
     *
     * @param eventProviderDefinition the {@link EventProviderDefinition} representing the {@link EventProvider} to
     *                                start
     * @throws NullPointerException if the provided {@code eventProviderDefinition} or {@code jarvisCore} is {@code
     *                              null}
     * @see EventProvider#run()
     * @see JarvisServer#registerWebhookEventProvider(WebhookEventProvider)
     */
    public final void startEventProvider(EventProviderDefinition eventProviderDefinition) {
        checkNotNull(eventProviderDefinition, "Cannot start the provided %s %s", EventProviderDefinition.class
                .getSimpleName(), eventProviderDefinition);
        checkNotNull(jarvisCore, "Cannot start the provided %s with the given %s %s", eventProviderDefinition
                .getClass().getSimpleName(), JarvisCore.class.getSimpleName(), jarvisCore);
        Log.info("Starting {0}", eventProviderDefinition.getName());
        String eventProviderQualifiedName = this.getClass().getPackage().getName() + ".io." + eventProviderDefinition
                .getName();
        Class<? extends EventProvider> eventProviderClass = Loader.loadClass(eventProviderQualifiedName,
                EventProvider.class);
        EventProvider eventProvider;
        try {
            eventProvider = Loader.construct(eventProviderClass, Arrays.asList
                    (JarvisCore.class, Configuration.class), Arrays
                    .asList(jarvisCore, configuration));
        } catch (NoSuchMethodException e) {
            Log.warn("Cannot find the method {0}({1},{2}), trying to initialize the EventProvider using its " +
                            "{0}({1}) constructor", eventProviderClass.getSimpleName(), JarvisCore.class
                            .getSimpleName(),
                    Configuration.class.getSimpleName());
            try {
                eventProvider = Loader.construct(eventProviderClass, JarvisCore.class, jarvisCore);
            } catch (NoSuchMethodException e1) {
                String errorMessage = MessageFormat.format("Cannot initialize {0}, the constructor {0}({1}) does " +
                        "not exist", eventProviderClass.getSimpleName(), JarvisCore.class.getSimpleName());
                Log.error(errorMessage);
                throw new JarvisException(errorMessage, e1);
            }
        }
        if (eventProvider instanceof WebhookEventProvider) {
            /*
             * Register the WebhookEventProvider in the JarvisServer
             */
            Log.info("Registering {0} in the {1}", eventProvider, JarvisServer.class.getSimpleName());
            jarvisCore.getJarvisServer().registerWebhookEventProvider((WebhookEventProvider) eventProvider);
        }
        Log.info("Starting EventProvider {0}", eventProviderClass.getSimpleName());
        EventProviderThread eventProviderThread = new EventProviderThread(eventProvider);
        eventProviderMap.put(eventProviderDefinition.getName(), eventProviderThread);
        eventProviderThread.start();
    }

    /**
     * Returns {@link Map} containing the {@link EventProviderThread}s associated to this module.
     * <b>Note:</b> this method is protected for testing purposes, and should not be called by client code.
     *
     * @return the {@link Map} containing the {@link EventProviderThread}s associated to this module
     */
    protected Map<String, EventProviderThread> getEventProviderMap() {
        return eventProviderMap;
    }

    /**
     * Retrieves and loads the {@link JarvisAction} defined by the provided {@link Action}.
     * <p>
     * This method loads the corresponding {@link JarvisAction} based on jarvis' naming convention. The
     * {@link JarvisAction} must be located under the {@code action} sub-package of the {@link JarvisModule}
     * concrete subclass package.
     *
     * @param action the {@link Action} definition representing the {@link JarvisAction} to enable
     * @see Loader#loadClass(String, Class)
     */
    public final void enableAction(Action action) {
        String actionQualifiedName = this.getClass().getPackage().getName() + ".action." + action.getName();
        Class<? extends JarvisAction> jarvisAction = Loader.loadClass(actionQualifiedName, JarvisAction.class);
        actionMap.put(action.getName(), jarvisAction);
    }

    /**
     * Disables the {@link JarvisAction} defined by the provided {@link Action}.
     *
     * @param action the {@link Action} definition representing the {@link JarvisAction} to disable
     */
    public final void disableAction(Action action) {
        actionMap.remove(action.getName());
    }

    /**
     * Disables all the {@link JarvisAction}s of the {@link JarvisModule}.
     */
    public final void disableAllActions() {
        actionMap.clear();
    }

    public final Class<? extends JarvisAction> getAction(String actionName) {
        return actionMap.get(actionName);
    }

    /**
     * Returns all the {@link JarvisAction} {@link Class}es associated to this {@link JarvisModule}.
     * <p>
     * This method returns the {@link Class}es describing the {@link JarvisAction}s associated to this module. To
     * construct a new {@link JarvisAction} from a {@link EventInstance} see
     * {@link #createJarvisAction(ActionInstance, JarvisSession)} .
     *
     * @return all the {@link JarvisAction} {@link Class}es associated to this {@link JarvisModule}
     * @see #createJarvisAction(ActionInstance, JarvisSession)
     */
    public final Collection<Class<? extends JarvisAction>> getActions() {
        return actionMap.values();
    }

    /**
     * Creates a new {@link JarvisAction} instance from the provided {@link ActionInstance}.
     * <p>
     * This methods attempts to construct a {@link JarvisAction} defined by the provided {@code actionInstance} by
     * matching the {@code eventInstance} variables to the {@link Action}'s parameters, and reusing the provided
     * {@link ActionInstance#getValues()}.
     *
     * @param actionInstance the {@link ActionInstance} representing the {@link JarvisAction} to create
     * @param session        the {@link JarvisSession} associated to the action
     * @return a new {@link JarvisAction} instance from the provided {@link ActionInstance}
     * @throws NullPointerException if the provided {@code actionInstance} or {@code session} is {@code null}
     * @throws JarvisException      if the provided {@link Action} does not match any {@link JarvisAction}, or if the
     *                              provided {@link EventInstance} does not define all the parameters required by the
     *                              action's constructor
     * @see #getParameterValues(ActionInstance, JarvisContext)
     */
    public JarvisAction createJarvisAction(ActionInstance actionInstance, JarvisSession
            session) {
        checkNotNull(actionInstance, "Cannot construct a %s from the provided %s %s", JarvisAction.class
                .getSimpleName(), ActionInstance.class.getSimpleName(), actionInstance);
        checkNotNull(session, "Cannot construct a %s from the provided %s %s", JarvisAction.class.getSimpleName(),
                JarvisSession.class.getSimpleName(), session);
        Action action = actionInstance.getAction();
        Class<? extends JarvisAction> jarvisActionClass = actionMap.get(action.getName());
        if (isNull(jarvisActionClass)) {
            throw new JarvisException(MessageFormat.format("Cannot create the JarvisAction {0}, the action is not " +
                    "loaded in the module", action.getName()));
        }
        Object[] parameterValues = getParameterValues(actionInstance, session.getJarvisContext());
        /*
         * Append the mandatory parameters to the parameter values.
         */
        Object[] fullParameters = new Object[parameterValues.length + 2];
        fullParameters[0] = this;
        fullParameters[1] = session;
        JarvisAction jarvisAction;
        if (parameterValues.length > 0) {
            System.arraycopy(parameterValues, 0, fullParameters, 2, parameterValues.length);
        }
        try {
            /**
             * The types of the parameters are not known, use {@link Loader#construct(Class, Object[])} to try to
             * find a constructor that accepts them.
             */
            jarvisAction = Loader.construct(jarvisActionClass, fullParameters);
        } catch (NoSuchMethodException e) {
            throw new JarvisException(MessageFormat.format("Cannot find a {0} constructor for the provided parameter " +
                    "types ({1})", jarvisActionClass.getSimpleName(), printClassArray(fullParameters)), e);
        }
        if (nonNull(actionInstance.getReturnVariable())) {
            jarvisAction.setReturnVariable(actionInstance.getReturnVariable().getReferredVariable().getName());
        }
        jarvisAction.init();
        return jarvisAction;
    }

    /**
     * Shuts down the {@link JarvisModule}.
     * <p>
     * This method attempts to terminate all the running {@link EventProvider} threads, close the corresponding
     * {@link EventProvider}s, and disables all the module's actions.
     *
     * @see EventProvider#close()
     * @see #disableAllActions()
     */
    public void shutdown() {
        Collection<EventProviderThread> threads = this.eventProviderMap.values();
        for (EventProviderThread thread : threads) {
            thread.getEventProvider().close();
            thread.interrupt();
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Log.warn("Caught an {0} while waiting for {1} thread to finish", e.getClass().getSimpleName(), thread
                        .getEventProvider().getClass().getSimpleName());
            }
        }
        this.eventProviderMap.clear();
        /*
         * Disable the actions at the end, in case a running EventProviderThread triggers an action computation
         * before it is closed.
         */
        this.disableAllActions();
    }

    /**
     * Retrieves the {@code actionInstance}'s parameter values from the provided {@code context}.
     * <p>
     * This method iterates through the {@link ActionInstance}'s {@link ParameterValue}s and matches them
     * against the describing {@link Action}'s {@link Parameter}s. The concrete value associated to the
     * {@link ActionInstance}'s {@link ParameterValue}s are retrieved from the provided {@code context}.
     * <p>
     * The retrieved values are used by the {@link JarvisModule} to instantiate concrete {@link JarvisAction}s (see
     * {@link #createJarvisAction(ActionInstance, JarvisSession)}).
     *
     * @param actionInstance the {@link ActionInstance} to match the parameters from
     * @return an array containing the concrete {@link ActionInstance}'s parameters
     * @throws JarvisException if one of the concrete value is not stored in the provided {@code context}, or if the
     *                         {@link ActionInstance}'s {@link ParameterValue}s do not match the describing
     *                         {@link Action}'s {@link Parameter}s.
     * @see #createJarvisAction(ActionInstance, JarvisSession)
     */
    private Object[] getParameterValues(ActionInstance actionInstance, JarvisContext context) {
        Action action = actionInstance.getAction();
        List<Parameter> actionParameters = action.getParameters();
        List<ParameterValue> actionInstanceParameterValues = actionInstance.getValues();
        if ((actionParameters.size() == actionInstanceParameterValues.size())) {
            /*
             * Here some additional checks are needed (parameter types and order).
             * See https://github.com/gdaniel/jarvis/issues/4.
             */
            Object[] actionInstanceParameterValuesArray = StreamSupport.stream(actionInstanceParameterValues
                    .spliterator(), false).map(paramValue -> {
                Expression paramExpression = paramValue.getExpression();
                if (paramExpression instanceof VariableAccess) {
                    String variableName = ((VariableAccess) paramExpression).getReferredVariable().getName();
                    Future<Object> futureValue = (Future<Object>) context.getContextValue("variables", variableName);
                    if (isNull(futureValue)) {
                        /*
                         * The context does not contain the value.
                         */
                        throw new JarvisException(MessageFormat.format("Cannot retrieve the context variable {0}" +
                                ".{1}", "variables", variableName));
                    }
                    try {
                        Object value = futureValue.get();
                        if (value instanceof String) {
                            /*
                             * Fill potential context values if the variable is a String. This should only happen if
                             * the associated JarvisAction returns a value with explicit access to the context in it.
                             */
                            return context.fillContextValues((String) value);
                        } else {
                            return value;
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        throw new JarvisException(e);
                    }
                } else if (paramExpression instanceof StringLiteral) {
                    return context.fillContextValues(((StringLiteral) paramExpression).getValue());
                } else {
                    /*
                     * Unknown Value type.
                     */
                    throw new JarvisException(MessageFormat.format("Unknown {0} expression type: {1}", ParameterValue
                            .class.getSimpleName(), nonNull(paramExpression) ? paramExpression.getClass()
                            .getSimpleName() : "null"));
                }
            }).toArray();
            return actionInstanceParameterValuesArray;
        }
        String errorMessage = MessageFormat.format("The action does not define the good amount of parameters: " +
                "expected {0}, found {1}", actionParameters.size(), actionInstanceParameterValues.size());
        Log.error(errorMessage);
        throw new JarvisException(errorMessage);
    }

    /**
     * Formats the provided {@code array} in a {@link String} used representing their {@link Class}es.
     * <p>
     * The returned {@link String} is "a1.getClass().getSimpleName(), a2.getClass().getSimpleName(), an.getClass()
     * .getSimpleName()", where <i>a1</i>, <i>a2</i>, and <i>an</i> are elements in the provided {@code array}.
     *
     * @param array the array containing the elements to print the {@link Class}es of
     * @return a {@link String} containing the formatted elements' {@link Class}es
     */
    private String printClassArray(Object[] array) {
        List<String> toStringList = StreamSupport.stream(Arrays.asList(array).spliterator(), false).map(o ->
        {
            if (isNull(o)) {
                return "null";
            } else {
                return o.getClass().getSimpleName();
            }
        }).collect(Collectors.toList());
        return String.join(", ", toStringList);
    }

    /**
     * The {@link Thread} class used to start {@link EventProvider}s.
     * <p>
     * <b>Note:</b> this class is protected for testing purposes, and should not be called by client code.
     */
    protected static class EventProviderThread extends Thread {

        /**
         * The {@link EventProvider} run by this {@link Thread}.
         */
        private EventProvider eventProvider;

        /**
         * Constructs a new {@link EventProviderThread} to run the provided {@code eventProvider}
         *
         * @param eventProvider the {@link EventProvider} to run
         */
        public EventProviderThread(EventProvider eventProvider) {
            super(eventProvider);
            this.eventProvider = eventProvider;
        }

        /**
         * Returns the {@link EventProvider} run by this {@link Thread}.
         *
         * @return the {@link EventProvider} run by this {@link Thread}
         */
        public EventProvider getEventProvider() {
            return eventProvider;
        }

    }
}
