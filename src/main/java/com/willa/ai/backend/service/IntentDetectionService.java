package com.willa.ai.backend.service;

import org.springframework.stereotype.Service;
import java.util.regex.Pattern;
import java.util.List;

@Service
public class IntentDetectionService {

    private static final List<Pattern> COMPLEX_PATTERNS = List.of(
        Pattern.compile("tại sao|vì sao|so sánh|khác nhau", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bottleneck|chậm nhất|hiệu quả|cải thiện", Pattern.CASE_INSENSITIVE),
        Pattern.compile("tổng quan|overview|báo cáo|report", Pattern.CASE_INSENSITIVE),
        Pattern.compile("đề xuất|recommend|nên làm gì|kế hoạch", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> DEADLINE_PATTERNS = List.of(
        Pattern.compile("deadline|hết hạn|hạn nộp|due date|còn bao (lâu|ngày)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("nộp (bài|task) (trước|vào) (ngày|lúc)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("hôm nay có (deadline|hạn) gì", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> STATUS_PATTERNS = List.of(
        Pattern.compile("chưa nộp|chưa xong|quá hạn|overdue|trễ", Pattern.CASE_INSENSITIVE),
        Pattern.compile("đã (hoàn thành|xong|nộp|làm xong)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("trạng thái (task|bài|project)", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> COUNT_PATTERNS = List.of(
        Pattern.compile("bao nhiêu (task|người|nhóm|bài)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("có (mấy|bao nhiêu)", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> LIST_PATTERNS = List.of(
        Pattern.compile("danh sách|liệt kê|show|list", Pattern.CASE_INSENSITIVE),
        Pattern.compile("tất cả (task|thành viên|nhóm)", Pattern.CASE_INSENSITIVE)
    );

    private static final List<Pattern> CREATE_TASK_PATTERNS = List.of(
        Pattern.compile("tạo task|thêm task|create task|làm task mới", Pattern.CASE_INSENSITIVE),
        Pattern.compile("tạo (cho tôi )?(một )?task", Pattern.CASE_INSENSITIVE),
        Pattern.compile("thêm (cho tôi )?(một )?task", Pattern.CASE_INSENSITIVE)
    );

    public String detectIntent(String question) {
        for (Pattern p : CREATE_TASK_PATTERNS) {
            if (p.matcher(question).find()) return "CREATE_TASK";
        }
        for (Pattern p : COMPLEX_PATTERNS) {
            if (p.matcher(question).find()) return "COMPLEX";
        }

        for (Pattern p : DEADLINE_PATTERNS) {
            if (p.matcher(question).find()) return "DEADLINE";
        }

        for (Pattern p : STATUS_PATTERNS) {
            if (p.matcher(question).find()) return "STATUS";
        }

        for (Pattern p : COUNT_PATTERNS) {
            if (p.matcher(question).find()) return "COUNT";
        }

        for (Pattern p : LIST_PATTERNS) {
            if (p.matcher(question).find()) return "LIST";
        }

        return "SIMPLE";
    }
}
