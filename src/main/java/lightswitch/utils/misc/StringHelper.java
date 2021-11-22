package lightswitch.utils.misc;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class StringHelper {
    public static String stripName(String playerName, String msg) {
        return msg.replace(playerName, "");
    }

    public static String randomizeCase(String str) {
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(str.length());
        for (char c : str.toCharArray())
            sb.append(rnd.nextBoolean()
                    ? Character.toLowerCase(c)
                    : Character.toUpperCase(c));
        return sb.toString();
    }

    public static String makeTS(Long millis) {
        return String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)),
                TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis)));
    }

}
