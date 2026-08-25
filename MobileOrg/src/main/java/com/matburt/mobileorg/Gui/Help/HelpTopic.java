package com.matburt.mobileorg.Gui.Help;

import android.content.Context;

import com.matburt.mobileorg.R;

public class HelpTopic {
    public final int titleRes;
    public final String fileName;

    public HelpTopic(int titleRes, String fileName) {
        this.titleRes = titleRes;
        this.fileName = fileName;
    }

    public static final HelpTopic[] TOPICS = {
            new HelpTopic(R.string.help_topic_quick_start, "quick-start.html"),
            new HelpTopic(R.string.help_topic_sync, "sync.html"),
            new HelpTopic(R.string.help_topic_outline, "outline.html"),
            new HelpTopic(R.string.help_topic_pomodoro, "pomodoro.html"),
            new HelpTopic(R.string.help_topic_statistics, "statistics.html"),
            new HelpTopic(R.string.help_topic_reminders, "reminders.html"),
            new HelpTopic(R.string.help_topic_extras, "extras.html"),
    };

    public static String getLangDir(Context context) {
        String lang = context.getResources().getConfiguration().locale.getLanguage();
        return "zh".equals(lang) ? "zh" : "en";
    }

    public static String getAssetPath(Context context, HelpTopic topic) {
        return "help/" + getLangDir(context) + "/" + topic.fileName;
    }
}
