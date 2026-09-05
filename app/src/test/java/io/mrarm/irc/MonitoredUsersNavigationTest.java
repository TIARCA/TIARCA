package io.mrarm.irc;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.UUID;

import io.mrarm.irc.config.ServerConfigData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class MonitoredUsersNavigationTest {

    private Context context;
    private ServerConnectionManager manager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        manager = ServerConnectionManager.getInstance(context);
    }

    @Test
    public void contextualEntryCreatesIntentWithServerUuid() {
        UUID uuidA = UUID.randomUUID();
        ServerConfigData config = new ServerConfigData();
        config.uuid = uuidA;
        ServerConnectionInfo connection = new ServerConnectionInfo(manager, config, null, null, null);

        Intent intent = MonitoredUsersActivity.createLaunchIntent(context, connection);

        assertNotNull(intent);
        assertEquals(MonitoredUsersActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals(uuidA.toString(), intent.getStringExtra(MonitoredUsersActivity.ARG_SERVER_UUID));
    }

    @Test
    public void globalEntryCreatesIntentForMonitoredServersActivity() {
        Intent intentNullConnection = MonitoredUsersActivity.createLaunchIntent(context, null);

        assertNotNull(intentNullConnection);
        assertEquals(MonitoredServersActivity.class.getName(), intentNullConnection.getComponent().getClassName());
        assertNull(intentNullConnection.getStringExtra(MonitoredUsersActivity.ARG_SERVER_UUID));

        ServerConnectionInfo connectionWithoutUuid = new ServerConnectionInfo(manager, new ServerConfigData(), null, null, null);
        Intent intentNoUuid = MonitoredUsersActivity.createLaunchIntent(context, connectionWithoutUuid);

        assertNotNull(intentNoUuid);
        assertEquals(MonitoredServersActivity.class.getName(), intentNoUuid.getComponent().getClassName());
        assertNull(intentNoUuid.getStringExtra(MonitoredUsersActivity.ARG_SERVER_UUID));
    }

    @Test
    public void createLaunchIntentForServerHandlesNullUuidFallback() {
        UUID uuidB = UUID.randomUUID();
        Intent intentWithUuid = MonitoredUsersActivity.createLaunchIntentForServer(context, uuidB);
        assertEquals(MonitoredUsersActivity.class.getName(), intentWithUuid.getComponent().getClassName());
        assertEquals(uuidB.toString(), intentWithUuid.getStringExtra(MonitoredUsersActivity.ARG_SERVER_UUID));

        Intent intentNullUuid = MonitoredUsersActivity.createLaunchIntentForServer(context, null);
        assertEquals(MonitoredServersActivity.class.getName(), intentNullUuid.getComponent().getClassName());
        assertNull(intentNullUuid.getStringExtra(MonitoredUsersActivity.ARG_SERVER_UUID));
    }
}
