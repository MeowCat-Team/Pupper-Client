package cn.pupperclient.utils.network;

/** Marks contents decoded from a remote server, without changing their serialized form. */
public interface ServerTextContents {
    void pupper$markServerSupplied();
}
