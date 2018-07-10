package fr.zelus.jarvis.plugins.log.module.action;

import fr.zelus.jarvis.core.session.JarvisSession;

public class LogWarningTest extends LogActionTest {

    private static String WARNING_TAG = "[WARN]";

    @Override
    protected LogAction createLogAction(String message) {
        return new LogWarning(logModule, new JarvisSession("id"), message);
    }

    @Override
    protected String expectedLogTag() {
        return WARNING_TAG;
    }
}