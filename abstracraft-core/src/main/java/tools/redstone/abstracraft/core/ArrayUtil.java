package tools.redstone.abstracraft.core;

import java.util.function.Predicate;

public class ArrayUtil {

    public static <T> boolean anyMatch(T[] arr, Predicate<T> predicate) {
        for (int i = 0, n = arr.length; i < n; i++)
            if (predicate.test(arr[i]))
                return true;
        return false;
    }

}
