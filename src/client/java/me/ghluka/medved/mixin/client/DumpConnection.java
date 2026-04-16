package me.ghluka.medved.mixin.client;
import net.minecraft.network.Connection;
public class DumpConnection {
    public static void test() {
        for (java.lang.reflect.Method m : Connection.class.getDeclaredMethods()) {
            if (m.getName().equals("send")) {
                System.out.println(m);
            }
        }
    }
}
