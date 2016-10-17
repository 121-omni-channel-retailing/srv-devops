package otocloud.server.common;

/**
 * TODO: DOCUMENT ME!
 *
 * @author liusya@yonyou.com
 * @date 9/18/15.
 */
public interface MessageBuilder<T> {

    String STATUS = "status";
    String ACTION = "action";

    public T create();
}
