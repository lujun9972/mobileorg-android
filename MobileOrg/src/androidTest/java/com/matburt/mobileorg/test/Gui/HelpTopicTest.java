package com.matburt.mobileorg.test.Gui;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.matburt.mobileorg.Gui.Help.HelpTopic;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class HelpTopicTest {

    private Context targetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void topicsCountMatchesDocuments() {
        assertEquals(9, HelpTopic.TOPICS.length);
    }

    @Test
    public void fileNamesAreUnique() {
        assertEquals(9, java.util.Arrays.stream(HelpTopic.TOPICS)
                .map(t -> t.fileName).distinct().count());
    }

    @Test
    public void allTopicsExistInBothLanguages() throws IOException {
        for (HelpTopic topic : HelpTopic.TOPICS) {
            for (String lang : new String[]{"zh", "en"}) {
                InputStream in = targetContext().getAssets()
                        .open("help/" + lang + "/" + topic.fileName);
                in.close();
            }
        }
    }

    @Test
    public void sharedStylesheetExists() throws IOException {
        targetContext().getAssets().open("help/help.css").close();
    }
}
