import com.github.warren_bank.mock_location.data_model.*;
import com.github.warren_bank.mock_location.util.Gcj02;
import com.github.warren_bank.mock_location.util.RouteParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RouteCheck {
    interface Action { void run() throws Exception; }
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    static void reject(Action action) throws Exception {
        try { action.run(); } catch (Exception expected) { checks++; return; }
        throw new AssertionError("invalid input accepted at " + checks);
    }
    static List<LocPoint> gpx(String xml) throws Exception {
        return RouteParser.gpx(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    public static void main(String[] args) throws Exception {
        List<LocPoint> points = RouteParser.text("0,0\n0,0.001\n0.001,0.001\n");
        check(points.size() == 3);
        check(RouteParser.draft("0,0").size() == 1 && RouteParser.draft("").isEmpty());
        check(RouteParser.text(RouteParser.format(points)).size() == 3);
        double length = RoutePlayback.distance(points.get(0), points.get(1));
        check(Math.abs(length - 111.195) < 0.01);
        RoutePlayback route = new RoutePlayback(points, length / 10, 1000);
        check(route.position(1000).equals(points.get(0), 1e-9));
        check(route.position(6000).equals(new LocPoint(0, .0005), 1e-9));
        route.setPaused(true, 6000);
        check(route.speed() == 0);
        check(route.position(16000).equals(new LocPoint(0, .0005), 1e-9));
        route.setPaused(false, 16000);
        check(route.position(21000).equals(points.get(1), 1e-9));
        check(Math.abs(route.speed() - length / 10) < 1e-5);
        check(route.position(26000).equals(new LocPoint(.0005, .001), 1e-9));
        check(route.position(31001).equals(points.get(2), 1e-9));
        check(route.isFinished() && route.speed() == 0);
        check(route.position(100000).equals(points.get(2), 1e-9));
        RoutePlayback delayed = new RoutePlayback(points, length / 10, 0);
        check(delayed.position(15000).equals(new LocPoint(.0005,.001), 1e-9));
        RoutePlayback duplicate = new RoutePlayback(Arrays.asList(points.get(0), points.get(0), points.get(1)), length / 2, 0);
        check(duplicate.position(1000).equals(new LocPoint(0,.0005),1e-9));
        LocPoint wrap = LocPoint.interpolate(new LocPoint(0,179), new LocPoint(0,-179), .5);
        check(Math.abs(wrap.getLongitude()) == 180);
        reject(() -> new LocPoint("NaN,0")); reject(() -> new LocPoint("1,Infinity"));
        reject(() -> new LocPoint(91,0)); reject(() -> new LocPoint(0,-181));
        reject(() -> new LocPoint("0,0,"));
        reject(() -> new RoutePlayback(points, 0, 0)); reject(() -> new RoutePlayback(points, Double.NaN, 0));
        reject(() -> new RoutePlayback(Arrays.asList(points.get(0), points.get(0)), 1, 0));
        reject(() -> RouteParser.text("0,0"));
        LocPoint shanghai = new LocPoint(31.2304, 121.4737), mapped = Gcj02.fromWgs84(shanghai);
        check(RoutePlayback.distance(shanghai, mapped) > 100);
        check(RoutePlayback.distance(shanghai, Gcj02.toWgs84(mapped.getLatitude(), mapped.getLongitude())) < .1);
        String track = "<gpx xmlns='http://www.topografix.com/GPX/1/1'><trk><trkseg><trkpt lat='0' lon='0'/><trkpt lat='0' lon='.001'/></trkseg></trk></gpx>";
        check(gpx(track).size()==2);
        reject(() -> gpx(track + track));
        reject(() -> gpx("<gpx xmlns='http://www.topografix.com/GPX/1/1'><trk xmlns='urn:foreign'><trkseg xmlns='http://www.topografix.com/GPX/1/1'><trkpt lat='0' lon='0'/><trkpt lat='1' lon='1'/></trkseg></trk></gpx>"));
        check(gpx("<gpx><rte><rtept lat='0' lon='0'/><rtept lat='1' lon='1'/></rte></gpx>").size()==2);
        reject(() -> gpx(track.replace("lat='0'", "lat='NaN'")));
        reject(() -> gpx(track.replace("lon='.001'", "")));
        reject(() -> gpx(track.replace("</trk>", "<trkseg/></trk>")));
        reject(() -> gpx(track.replace("</gpx>", "<rte/></gpx>")));
        reject(() -> gpx(track.replace("</gpx>", "")));
        reject(() -> gpx("<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" + track));
        reject(() -> gpx("<html><trkpt lat='0' lon='0'/><trkpt lat='1' lon='1'/></html>"));
        reject(() -> gpx("<gpx><wpt lat='0' lon='0'/><wpt lat='1' lon='1'/></gpx>"));
        reject(() -> gpx("<gpx xmlns='urn:wrong'><rte><rtept lat='0' lon='0'/><rtept lat='1' lon='1'/></rte></gpx>"));
        reject(() -> gpx(track.replace("</gpx>", "<desc>" + new String(new char[RouteParser.MAX_BYTES]).replace('\0', 'x') + "</desc></gpx>")));
        StringBuilder huge = new StringBuilder();
        for (int i=0;i<10001;i++) huge.append("0,0\n");
        reject(() -> RouteParser.text(huge.toString()));
        System.out.println("PASS: " + checks + " route/parser checks");
    }
}
