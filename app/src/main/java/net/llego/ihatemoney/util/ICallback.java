package net.llego.ihatemoney.util;

/**
 * Callback
 */
public interface ICallback {
    void onFinish();

    void onFinish(String result, String message);

    void onScheduled();
}
