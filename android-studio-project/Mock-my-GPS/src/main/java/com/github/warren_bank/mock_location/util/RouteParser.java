package com.github.warren_bank.mock_location.util;

import com.github.warren_bank.mock_location.data_model.LocPoint;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public final class RouteParser {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private RouteParser() {}

    public static List<LocPoint> text(String text) {
        List<LocPoint> result = draft(text);
        if (result.size() < 2) throw new IllegalArgumentException("路线至少需要两个点");
        return result;
    }

    public static List<LocPoint> draft(String text) {
        if (text == null || text.length() > MAX_BYTES) throw new IllegalArgumentException("路线文本过大");
        List<LocPoint> result = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) add(result, new LocPoint(line));
        }
        return result;
    }

    public static String format(List<LocPoint> points) {
        StringBuilder text = new StringBuilder();
        for (LocPoint p : points) text.append(p).append('\n');
        return text.toString();
    }

    public static List<LocPoint> gpx(InputStream source) throws Exception {
        if (source == null) throw new IOException("无法读取 GPX");
        InputStream bounded = new FilterInputStream(source) {
            int remaining = MAX_BYTES;
            public int read() throws IOException {
                int value = super.read();
                if (value != -1 && --remaining < 0) throw new IOException("GPX 超过 2 MiB");
                return value;
            }
            public int read(byte[] b, int off, int len) throws IOException {
                int count = in.read(b, off, Math.min(len, Math.max(1, remaining + 1)));
                if (count > 0 && (remaining -= count) < 0) throw new IOException("GPX 超过 2 MiB");
                return count;
            }
        };
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(bounded, null);
        List<LocPoint> result = new ArrayList<>();
        ArrayList<String> path = new ArrayList<>();
        String namespace = null;
        int tracks = 0, segments = 0, routes = 0;
        for (int event = parser.nextToken(); event != XmlPullParser.END_DOCUMENT; event = parser.nextToken()) {
            if (event == XmlPullParser.DOCDECL || event == XmlPullParser.ENTITY_REF)
                throw new IllegalArgumentException("GPX 不允许 DTD 或实体引用");
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                path.add(name);
                if (path.size() > 32) throw new IllegalArgumentException("GPX 嵌套过深");
                if (path.size() == 1) {
                    if (namespace != null) throw new IllegalArgumentException("GPX 只能有一个根元素");
                    namespace = parser.getNamespace();
                    if (!"gpx".equals(name) || !(namespace.isEmpty() || namespace.equals("http://www.topografix.com/GPX/1/1") || namespace.equals("http://www.topografix.com/GPX/1/0")))
                        throw new IllegalArgumentException("不是支持的 GPX 文件");
                }
                if (!parser.getNamespace().equals(namespace)) { path.set(path.size() - 1, "#foreign"); continue; }
                String parent = path.size() > 1 ? path.get(path.size() - 2) : "";
                if (path.size() == 2 && name.equals("trk")) tracks++;
                if (path.size() == 2 && name.equals("rte")) routes++;
                if (path.size() == 3 && parent.equals("trk") && name.equals("trkseg")) segments++;
                if (tracks > 1 || routes > 1 || segments > 1 || tracks + routes > 1)
                    throw new IllegalArgumentException("请导入单条路线或单段轨迹，避免跨段跳跃");
                boolean trackPoint = path.size() == 4 && path.get(1).equals("trk") && parent.equals("trkseg") && name.equals("trkpt");
                boolean routePoint = path.size() == 3 && parent.equals("rte") && name.equals("rtept");
                if (trackPoint || routePoint) {
                    String lat = parser.getAttributeValue(null, "lat"), lon = parser.getAttributeValue(null, "lon");
                    if (lat == null || lon == null) throw new IllegalArgumentException("GPX 点缺少经纬度");
                    add(result, new LocPoint(Double.parseDouble(lat), Double.parseDouble(lon)));
                }
            } else if (event == XmlPullParser.END_TAG) path.remove(path.size() - 1);
        }
        if (namespace == null || !path.isEmpty()) throw new IllegalArgumentException("GPX 文件不完整");
        return finish(result);
    }
    private static void add(List<LocPoint> points, LocPoint point) {
        if (points.size() >= 10000) throw new IllegalArgumentException("路线最多 10000 个点");
        points.add(point);
    }
    private static List<LocPoint> finish(List<LocPoint> points) {
        if (points.size() < 2) throw new IllegalArgumentException("路线至少需要两个点");
        return points;
    }
}
