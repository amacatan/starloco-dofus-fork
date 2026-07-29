package org.starloco.locos.tests;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IoSessionStub implements InvocationHandler {
    private final Map<Object, Object> attributes = new HashMap<>();
    private final List<Object> writes = new ArrayList<>();
    private final IoSession session;
    private int closeCount;

    public IoSessionStub() {
        this.session = (IoSession) Proxy.newProxyInstance(
                IoSession.class.getClassLoader(),
                new Class<?>[]{IoSession.class},
                this
        );
    }

    public IoSession session() {
        return session;
    }

    public int closeCount() {
        return closeCount;
    }

    public String lastPacket() {
        if (writes.isEmpty()) {
            return null;
        }

        Object value = writes.get(writes.size() - 1);
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof IoBuffer) {
            IoBuffer buffer = (IoBuffer) value;
            int position = buffer.position();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            buffer.position(position);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if ("getId".equals(name)) {
            return 1L;
        }
        if ("getRemoteAddress".equals(name)) {
            return new InetSocketAddress("127.0.0.1", 12345);
        }
        if ("getAttribute".equals(name)) {
            Object value = attributes.get(args[0]);
            return value != null || args.length == 1 ? value : args[1];
        }
        if ("setAttribute".equals(name)) {
            return attributes.put(args[0], args[1]);
        }
        if ("write".equals(name)) {
            writes.add(args[0]);
            return null;
        }
        if ("close".equals(name) || "closeNow".equals(name) || "closeOnFlush".equals(name)) {
            closeCount++;
            return null;
        }
        if ("isConnected".equals(name)) {
            return closeCount == 0;
        }
        if ("isClosing".equals(name)) {
            return closeCount > 0;
        }
        if ("toString".equals(name)) {
            return "IoSessionStub";
        }
        if ("hashCode".equals(name)) {
            return System.identityHashCode(proxy);
        }
        if ("equals".equals(name)) {
            return proxy == args[0];
        }

        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }
}
