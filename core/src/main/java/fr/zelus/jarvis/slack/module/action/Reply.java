package fr.zelus.jarvis.slack.module.action;

import fr.zelus.jarvis.core.session.JarvisContext;
import fr.zelus.jarvis.slack.JarvisSlackUtils;
import fr.zelus.jarvis.slack.module.SlackModule;

import static fr.inria.atlanmod.commons.Preconditions.checkArgument;
import static fr.inria.atlanmod.commons.Preconditions.checkNotNull;

public class Reply extends PostMessage {

    private static String getChannel(JarvisContext context) {
        Object channelValue = context.getContextValue(JarvisSlackUtils.SLACK_CONTEXT_KEY, JarvisSlackUtils
                .SLACK_CHANNEL_CONTEXT_KEY);
        checkNotNull(channelValue, "Cannot retrieve the Slack channel from the context");
        checkArgument(channelValue instanceof String, "Invalid Slack channel type, expected %s, found %s", String
                .class.getSimpleName(), channelValue.getClass().getSimpleName());
        return (String) channelValue;
    }

    public Reply(SlackModule containingModule, JarvisContext context, String message) {
        super(containingModule, context, message, getChannel(context));
    }
}
