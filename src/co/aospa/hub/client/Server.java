package co.aospa.hub.client;

import android.content.Context;

import java.io.File;

import co.aospa.hub.R;

public class Server {

    public static String getUrl(Context context) {
        return context.getResources().getString(R.string.system_update_server_url);
    }

    public static File getComponentFile(Context context, String file) {
        return new File(context.getCacheDir(), file);
    }
}
