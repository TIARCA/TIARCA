package io.mrarm.irc.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import io.mrarm.irc.setting.ReconnectIntervalSetting;

public class GsonTypeTokenTest {

    @Test public void mentionStorageRetainsRecordListType() throws Exception {
        Type type = staticType(MentionStorage.class, "RECORD_LIST_TYPE");
        assertListElement(type, "io.mrarm.irc.config.MentionStorage$Record");
    }

    @Test public void reconnectRulesRetainRuleListType() {
        assertListElement(ReconnectIntervalSetting.sListRuleType,
                "io.mrarm.irc.setting.ReconnectIntervalSetting$Rule");
    }

    private static Type staticType(Class<?> owner, String fieldName) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Type) field.get(null);
    }

    private static void assertListElement(Type type, String elementTypeName) {
        assertTrue(type instanceof ParameterizedType);
        ParameterizedType parameterized = (ParameterizedType) type;
        assertEquals(List.class, parameterized.getRawType());
        assertEquals(elementTypeName, parameterized.getActualTypeArguments()[0].getTypeName());
    }
}
