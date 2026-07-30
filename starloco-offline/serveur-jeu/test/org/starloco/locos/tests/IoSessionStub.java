package org.starloco.locos.tests;

import org.apache.mina.core.session.IoSession;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IoSessionStub implements InvocationHandler {
    private final Map<Object, Object> attributes = new HashMap<>();
    private final List<Object> writes = new ArrayList<>();
    private final IoSession session;
    private final SocketAddress remoteAddress;

    public IoSessionStub() {
        this(new InetSocketAddress("127.0.0.1", 12345));
    }

    public IoSessionStub(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
        this.session = (IoSession) Proxy.newProxyInstance(
                IoSession.class.getClassLoader(),
                new Class<?>[]{IoSession.class},
                this
        );
    }

    public IoSession session() {
        return session;
    }

    public int writeCount() {
        return writes.size();
    }

    public List<Object> writes() {
        return Collections.unmodifiableList(writes);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if ("getId".equals(name)) {
            return 1L;
        }
        if ("getRemoteAddress".equals(name)) {
            return remoteAddress;
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
        if ("isConnected".equals(name)) {
            return true;
        }
        if ("isClosing".equals(name)) {
            return false;
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
