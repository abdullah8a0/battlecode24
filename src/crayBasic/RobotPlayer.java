package crayBasic;

import battlecode.common.*;
import crayBasic.util.FastMath;

public strictfp class RobotPlayer {

    static int turnCount = 0;

    static int mapWidth, mapHeight;

    static Team myTeam;
    static Team oppTeam;
   
    public static String indicator;
    public static int startRound;

    static MapLocation[] allySpawnLocs;
    static MapLocation mySpawnLoc;

    static RobotController rc;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        RobotPlayer.rc = rc;
        myTeam = rc.getTeam();
        oppTeam = myTeam.opponent();
        indicator = "";
        mapWidth = rc.getMapWidth();
        mapHeight = rc.getMapHeight();
        allySpawnLocs = rc.getAllySpawnLocations();

        FastMath.initRand(rc);



        while (true) {
            try {
                startRound = rc.getRoundNum();
                Comm.turn_starts();
                // if (startRound == 3) rc.resign();
                // This is janky spawn handling. Maybe the next version will fix it.
                if (!rc.isSpawned()){
                    // Pick a random spawn location to attempt spawning in.
                    MapLocation randomLoc = allySpawnLocs[FastMath.rand256() % allySpawnLocs.length];
                    if (rc.canSpawn(randomLoc)) {
                        rc.spawn(randomLoc);
                        mySpawnLoc = randomLoc;
                        if (startRound == 1) {
                            // find flag and register it.
                            FlagInfo[] flags = rc.senseNearbyFlags(-1, myTeam);
                            for (FlagInfo flag: flags) {
                                Comm.flagInit(flag.getLocation());
                            }
                        } 
                    }
                } else {
                    Duck.run(rc);
                }
                rc.setIndicatorString(indicator);

                Comm.commit_write();

                // if (rc.getID() == 11480)
                //     Comm.print_debug();
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }

        // SELF DESTRUCT HERE 
    }
}

