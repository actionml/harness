package com.actionml;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author The ActionML Team (<a href="http://actionml.com">http://actionml.com</a>)
 * Created by Pavlenov Semen on 11.06.17.
 */
public class Utils {

    private static String getDistroName() throws IOException {
        Pattern distroRegex = Pattern.compile("[^(]+\\([^(]+\\([^(]+\\(([A-Za-z\\s]+).*");
        BufferedReader reader = new BufferedReader(new FileReader("/proc/version"));
        String distro;
        try {
            Matcher line = distroRegex.matcher(reader.readLine());
            distro = line.matches() ? line.group(1) : null;
        }
        finally {
            reader.close();
        }
        return distro;
    }

    private static String getLinuxDistro() throws IOException {
        BufferedReader reader = null;
        String release = null;
        String distro = getDistroName();
        try {
            Process process = Runtime.getRuntime().exec("lsb_release -r");
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            Pattern releasePattern = Pattern.compile("Release:\\s*(\\d+).*");
            Matcher matcher;
            while ((line = reader.readLine()) != null) {
                matcher = releasePattern.matcher(line);
                if (matcher.matches()) {
                    release = matcher.group(1);
                }
            }
        }
        finally {
            reader.close();
        }
        if (distro == null || release == null) {
            throw new UnsupportedEncodingException("Linux distro does not support lsb_release, cannot determine version, distro: " + distro + ", release: " + release);
        }

        return distro.trim().replaceAll(" ", "_") + "." + release;
    }

    private static String getOsFamily() throws IOException {
        final String osName = System.getProperty("os.name");
        if (osName.toLowerCase().contains("mac")) {
            return "Darwin";
        }
        else if (osName.toLowerCase().contains("linux")) {
            return getLinuxDistro();
        }
        throw new IllegalStateException("Unsupported operating system " + osName);
    }

    public static String loadOSDependentLibrary() throws IOException {
        String osFamily = getOsFamily();
        return osFamily + "." + System.getProperty("os.arch");

    }


}
