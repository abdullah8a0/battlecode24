package crayEcon;

import battlecode.common.*;
import crayEcon.util.FastIterableLocSet;

public class MapRecorder extends RobotPlayer {
    // perform bit hack to reduce init cost
    public static final char SEEN_BIT = 1 << 4;
    public static final char PASSABLE_BIT = 1 << 5;
    public static final char WALL_BIT = 1 << 6;
    public static final char WATER_BIT = 1 << 7;

    public static final char CURRENT_MASK = 0xF;
    // current use & 0xF for ordinal 

    public static char[] vals = Constants.MAP_LEN_STRING.toCharArray();

    public static boolean check(MapLocation loc, Direction targetDir) throws GameActionException {
        if (!rc.onTheMap(loc))
            return false;
        int val = vals[loc.x * mapHeight + loc.y];
        if ((val & SEEN_BIT) == 0) // always explore an unseen tile
            return true;
        if ((val & PASSABLE_BIT) == 0)
            return false;
        return true;
    }

    public static void recordSym(int leaveBytecodeCnt) throws GameActionException {
        MapInfo[] infos = rc.senseNearbyMapInfos();
        for (int i = infos.length; --i >= 0; ) {
            if (Clock.getBytecodesLeft() <= leaveBytecodeCnt) {
                return;
            }
            if ((vals[infos[i].getMapLocation().x * mapHeight + infos[i].getMapLocation().y] & SEEN_BIT) != 0)
                continue;
            MapInfo info = infos[i];
            int x = info.getMapLocation().x;
            int y = info.getMapLocation().y;
            char val = SEEN_BIT;
            if (info.isPassable())
                val |= PASSABLE_BIT;

            if (!Comm.isSymmetryConfirmed) {
                if (info.isWall())
                    val |= WALL_BIT;
                if (info.isWater())
                    val |= WATER_BIT;
                int symVal;
                for (int sym = 3; --sym >= 0; ) {
                    if (Comm.isSymEliminated[sym])
                        continue;
                    symVal = vals[((sym & 1) == 0 ? mapWidth - x - 1 : x) * mapHeight + ((sym & 2) == 0 ? mapHeight - y - 1 : y)];
                    if ((symVal & SEEN_BIT) == 0) {
                        continue;
                    }
                }
            }
            vals[x * mapHeight + y] = val;
        }
    }


    public static void reportEnemyHQ(MapLocation loc) {
        int hqX = loc.x;
        int hqY = loc.y;
        if (vals[hqX * mapHeight + hqY] == SEEN_BIT) {
            return;
        }
        // since this will mess with symmetry scouting, we disable checking and reporting
        Comm.isSymmetryConfirmed = true;
        Comm.needSymmetryReport = false;
        vals[hqX * mapHeight + hqY] = SEEN_BIT;
    }
}
