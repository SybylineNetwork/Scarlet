package net.sybyline.scarlet.util;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

public class GDrive
{

    static final Logger LOG = LoggerFactory.getLogger("GDrive");

    public GDrive(File driveRoot, File storeDir, String in) throws IOException
    {
        NetHttpTransport transport = new NetHttpTransport();
        JsonFactory json = GsonFactory.getDefaultInstance();
        GoogleClientSecrets secrets = GoogleClientSecrets.load(json, new StringReader(in));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow
                .Builder(transport, json, secrets, Arrays.asList(DriveScopes.DRIVE_READONLY))
                .setDataStoreFactory(new FileDataStoreFactory(storeDir))
                .setAccessType("offline")
                .build();
        Credential cred = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
                .authorize("user");
        Drive drive = new Drive
                .Builder(transport, json, cred)
                .setApplicationName("GDLinkTranslatorClient")
                .build();
        this.driveRoot = driveRoot;
        this.drive = drive;
    }

    final File driveRoot;
    final Drive drive;

    public String findId(String name)
    {
        try
        {
            return this
                .drive
                .files()
                .list()
                .setQ("name='"+name+"'")
                .setFields("files(id)")
                .execute()
                .getFiles()
                .stream()
                .findFirst()
                .map(com.google.api.services.drive.model.File::getId)
                .orElse(null)
                ;
        }
        catch (IOException ioex)
        {
            LOG.error("Exception finding file", ioex);
            return null;
        }
    }

}
