package io.mrarm.irc.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import io.mrarm.irc.setting.ReconnectIntervalSetting;

public class GsonTypeTokenTest {

    @Test public void reconnectRulesRetainRuleListType() {
        assertListElement(ReconnectIntervalSetting.sListRuleType,
                "io.mrarm.irc.setting.ReconnectIntervalSetting$Rule");
    }

    @Test public void commandAliasManagerRetainsUserAliasesListType() {
        assertListElement(CommandAliasManager.USER_ALIASES_LIST_TYPE,
                "io.mrarm.irc.config.CommandAliasManager$CommandAlias");
    }

    private static void assertListElement(Type type, String elementTypeName) {
        assertTrue(type instanceof ParameterizedType);
        ParameterizedType parameterized = (ParameterizedType) type;
        assertEquals(List.class, parameterized.getRawType());
        assertEquals(elementTypeName, parameterized.getActualTypeArguments()[0].getTypeName());
    }
}
