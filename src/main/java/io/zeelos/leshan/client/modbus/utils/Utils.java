package io.zeelos.leshan.client.modbus.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {

    public static Map<Integer, Long> asMapInteger(int[] arr) {
        Map<Integer, Long> map = new HashMap<>();
        for (int i = 0; i < arr.length; i++) {
            map.put(i, (long) arr[i]);
        }

        return map;
    }

    public static int[] asArrInteger(Map map) {
        int[] arr = new int[map.size()];

        for (int i = 0; i < arr.length; i++) {
            arr[i] = (int) (long) map.get(i);
        }

        return arr;
    }


    public static Map<Integer, Boolean> asMapBoolean(boolean[] arr) {
        Map<Integer, Boolean> map = new HashMap<>();
        for (int i = 0; i < arr.length; i++) {
            map.put(i, arr[i]);
        }

        return map;
    }

    public static boolean[] asArrBoolean(Map map) {
        boolean[] arr = new boolean[map.size()];

        for (int i = 0; i < arr.length; i++) {
            arr[i] = (boolean) map.get(i);

        }

        return arr;
    }

    public static int[] toIntArray(List<Integer> list) {
        int[] result = new int[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = list.get(i);
        }
        return result;
    }
}
