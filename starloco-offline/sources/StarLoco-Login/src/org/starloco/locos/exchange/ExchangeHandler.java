package org.starloco.locos.exchange;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.starloco.locos.kernel.Console;
import org.starloco.locos.kernel.Logging;
import org.starloco.locos.login.LoginPacketRedactor;

import java.net.InetAddress;
import java.net.InetSocketAddress;

class ExchangeHandler implements IoHandler {

    @Override
    public void exceptionCaught(IoSession arg0, Throwable arg1) {
        this.setLogged(arg0, "exceptionCaught : " + arg1.getMessage());
        Console.instance.write("eSession " + arg0.getId() + " exception : " + arg1.getCause() + " : " + arg1.getMessage());
    }

    @Override
    public void messageReceived(IoSession arg0, Object arg1) {
        ExchangeClient client = (ExchangeClient) arg0.getAttribute("client");
        String message = bufferToString(arg1);
        String loggedMessage = LoginPacketRedactor.exchange(message);
        Console.instance.write("eSession " + arg0.getId() + " < " + loggedMessage);

        if (client.getServer() != null)
            Logging.getInstance().write("Game", "messageReceived de " + client.getServer().getId() + " : " + loggedMessage);
        else {
            InetSocketAddress socketAddress = (InetSocketAddress) arg0.getRemoteAddress();
            InetAddress inetAddress = socketAddress.getAddress();
            String IP = inetAddress.getHostAddress();
            Logging.getInstance().write("Game", "messageReceived de " + IP + " : " + loggedMessage);
        }
        client.parser(message);

    }

    @Override
    public void messageSent(IoSession arg0, Object arg1) {
        String message = bufferToString(arg1);
        this.setLogged(arg0, "messageSent : " + message);
        Console.instance.write("eSession " + arg0.getId() + " > " + message);
    }

    @Override
    public void inputClosed(IoSession ioSession) {
        ioSession.close(true);
    }

    @Override
    public void sessionClosed(IoSession arg0) {
        Console.instance.write("eSession " + arg0.getId() + " closed");
        this.setLogged(arg0, "sessionClosed");
        if (arg0.getAttribute("client") instanceof ExchangeClient) {
            ExchangeClient client = (ExchangeClient) arg0.getAttribute("client");
            if (client.getServer() != null && client.getServer().getClient() == client) {
                client.getServer().setClient(null);
                client.getServer().setState(0);
            }
        }
    }

    @Override
    public void sessionCreated(IoSession arg0) {
        Console.instance.write("eSession " + arg0.getId() + " created");
        arg0.setAttribute("client", new ExchangeClient(arg0));

        IoBuffer ioBuffer = IoBuffer.allocate(2048);
        ioBuffer.put("SK?".getBytes());
        ioBuffer.flip();
        arg0.write(ioBuffer);

        this.setLogged(arg0, "sessionCreated");
    }

    @Override
    public void sessionIdle(IoSession arg0, IdleStatus arg1) {
        this.setLogged(arg0, "sessionIdle");
        Console.instance.write("eSession " + arg0.getId() + " idle");
    }

    @Override
    public void sessionOpened(IoSession arg0) {
        this.setLogged(arg0, "sessionOpened");
    }

    String bufferToString(Object o) {
        IoBuffer data = (IoBuffer) o;
        byte[] buf = new byte[data.limit()];
        data.get(buf);
        return new String(buf);
    }

    private void setLogged(IoSession arg0, String msg) {
        Object attribute = arg0.getAttribute("client");
        if (attribute instanceof ExchangeClient) {
            ExchangeClient client = (ExchangeClient) attribute;
            if (client.getServer() != null) {
                Logging.getInstance().write("Game", msg + " -> " + client.getServer().getId());
                return;
            }
        }

        String endpoint = "unbound";
        if (arg0.getRemoteAddress() instanceof InetSocketAddress) {
            InetAddress address = ((InetSocketAddress) arg0.getRemoteAddress()).getAddress();
            if (address != null) {
                endpoint = address.getHostAddress();
            }
        }
        Logging.getInstance().write("Game", msg + " -> " + endpoint);
    }
}
