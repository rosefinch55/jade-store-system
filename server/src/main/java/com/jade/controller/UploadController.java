package com.jade.controller;

import com.jade.common.BizException;
import com.jade.common.RequirePerm;
import com.jade.common.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 图片上传：加工师傅提交完工要「加工前 / 加工中 / 完工」三张图，小程序端先传到
 * 这里拿相对路径，再随完工申请一起提交。
 *
 * <p>存到容器外的挂载目录，容器重建不丢；按年月分目录，避免单目录文件过多。
 */
@RestController
@RequestMapping("/api")
// 上传图片会永久落盘并对外可访问，只开放给店里人和加工师傅；
// 供应商 / 合作商户的页面没有上传入口（完工照片只在师傅面板），给它们开门只是多一个写盘口子
@RequirePerm(value = {}, roles = {"BOSS", "SALE", "CRAFTSMAN"})
public class UploadController {

    private static final Path ROOT = Paths.get("/data/uploads");
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UploadController.class);
    private static final List<String> ALLOWED = List.of("image/jpeg", "image/png", "image/webp", "image/heic");
    /** HEIF/HEIC 家族主品牌（ftyp 之后 4 字节），用来把 mp4/mov/m4a 这些同为 ISO-BMFF 的视频挡在外面 */
    private static final List<String> HEIC_BRANDS = List.of(
            "heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs", "mif1");

    /** 按文件头魔数判真实类型，不信任客户端报的 Content-Type 或扩展名 */
    private static String sniff(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) return "image/png";
        if (b.length >= 12 && b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46
                && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50) return "image/webp";
        // 只认 ftyp 的主品牌，光看偏移 4 的 "ftyp" 会把 mp4/mov 当成 heic 放行
        if (b.length >= 12 && b[4] == 0x66 && b[5] == 0x74 && b[6] == 0x79 && b[7] == 0x70) {
            String brand = new String(b, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                    .toLowerCase(java.util.Locale.ROOT);
            if (HEIC_BRANDS.contains(brand)) return "image/heic";
        }
        return null;
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) throw BizException.badRequest("请选择要上传的图片");
        if (file.getSize() > MAX_BYTES) throw BizException.badRequest("图片不能超过 8MB");
        String type = file.getContentType();
        if (type == null || !ALLOWED.contains(type.toLowerCase())) {
            throw BizException.badRequest("只支持 JPG / PNG / WEBP 图片");
        }
        byte[] head;
        try (java.io.InputStream in = file.getInputStream()) {
            head = in.readNBytes(16);
        } catch (IOException e) {
            throw BizException.badRequest("图片读取失败");
        }
        String real = sniff(head);
        if (real == null || !ALLOWED.contains(real)) {
            // 客户端报的 MIME 可伪造，真实字节不是图片就拒
            throw BizException.badRequest("文件内容不是有效的图片");
        }
        if (!real.equals(type.toLowerCase()) && !"image/jpeg".equals(real)) {
            type = real; // 以魔数为准定扩展名
        }

        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String ext = switch (type.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            default -> ".jpg";
        };
        Path dir = ROOT.resolve(month);
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(name).toFile());
        } catch (IOException e) {
            // 对外只给固定文案：e.getMessage() 里带 /data/uploads/... 的绝对路径
            log.warn("图片落盘失败", e);
            throw BizException.badRequest("图片保存失败，请重试");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", "/uploads/" + month + "/" + name);
        data.put("size", file.getSize());
        return Result.ok(data);
    }

    public static class Base64Req {
        public String name;
        public String data;
    }

    /**
     * Base64 上传：小程序走云函数中转时发不出 multipart，只能把图片读成 base64
     * 塞进 JSON。这里解码落盘，路径规则与 multipart 一致。
     */
    @PostMapping("/upload/base64")
    public Result<Map<String, Object>> uploadBase64(@RequestBody Base64Req req) {
        if (req == null || req.data == null || req.data.isBlank()) {
            throw BizException.badRequest("没有收到图片数据");
        }
        String raw = req.data;
        int comma = raw.indexOf(',');
        if (raw.startsWith("data:") && comma > 0) raw = raw.substring(comma + 1);

        // 解码前先按 base64 长度拦一道：解码要再分配一个等大的 byte[]，
        // 容器只有 -Xmx448m，几百 MB 的 body 连着解码就是 OOM
        if (raw.length() > (MAX_BYTES / 3 + 1) * 4) {
            throw BizException.badRequest("图片不能超过 8MB");
        }

        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            throw BizException.badRequest("图片数据不是合法的 base64");
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw BizException.badRequest("图片不能超过 8MB");
        }
        String real = sniff(bytes);
        if (real == null || !ALLOWED.contains(real)) {
            throw BizException.badRequest("文件内容不是有效的图片");
        }
        String ext = switch (real) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            default -> ".jpg";
        };

        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        Path dir = ROOT.resolve(month);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), bytes);
        } catch (IOException e) {
            // 对外只给固定文案：e.getMessage() 里带 /data/uploads/... 的绝对路径
            log.warn("图片落盘失败", e);
            throw BizException.badRequest("图片保存失败，请重试");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("path", "/uploads/" + month + "/" + name);
        data.put("size", bytes.length);
        return Result.ok(data);
    }
}
