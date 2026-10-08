package com.yztc.auth.data;

/** 一条留言：私信（isPublic=false，to=收件人）或公开板（isPublic=true，to=""）。 */
public final class Mail {
    public String id;
    public String from;
    public String to;
    public String text;
    public long createdAt;
    public boolean isPublic;
    public long readAt;
}
