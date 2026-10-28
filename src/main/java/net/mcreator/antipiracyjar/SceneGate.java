package net.mcreator.antipiracyjar;

public final class SceneGate {
    private String type = "";
    private String owner = "";
    private long deadline;
    private long quietUntil;

    public boolean idle(long now) { return type.isEmpty() && now >= quietUntil; }
    public boolean active() { return !type.isEmpty(); }
    public String type() { return type; }
    public String owner() { return owner; }
    public long quietUntil() { return quietUntil; }
    public void restoreQuiet(long until) { quietUntil = Math.max(quietUntil,until); }
    public boolean matches(String kind, String who) { return type.equals(kind) && owner.equals(who); }

    public boolean start(long now, String kind, String who, long duration, boolean test) {
        if (active() || (!test && now < quietUntil)) return false;
        type=kind; owner=who; deadline=now+duration;
        return true;
    }

    public void renew(long now, String kind, String who, long duration) {
        if (matches(kind,who)) deadline=Math.max(deadline,now+duration);
    }

    public boolean finish(long now, String kind, String who, long pause) {
        if (!matches(kind,who)) return false;
        type=""; owner=""; deadline=0;
        quietUntil=Math.max(quietUntil,now+pause);
        return true;
    }

    public boolean expired(long now) { return active() && now >= deadline; }
}
