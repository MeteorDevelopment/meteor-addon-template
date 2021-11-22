package lightswitch.utils.misc;


import lightswitch.utils.chat.EzUtil;

public class Stats {
    public static int kills = 0;
    public static int deaths = 0;
    public static int killStreak = 0;
    public static int highscore = 0;
    public static long rpcStart = System.currentTimeMillis() / 1000L;

    public static void reset() {
        kills = 0;
        deaths = 0;
        killStreak = 0;
        highscore = 0;
        //BurrowAlert.burrowedPlayers.clear();
        EzUtil.currentTargets.clear();
    }
}
