package vn.actvn.onthionline.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {
    private Integer id;
    private String question;
    private String type;
    private String option1;
    private String option2;
    private String option3;
    private String option4;
    private String mode;
    private String grade;
    private String subject;
    private String suggestion;
    private List<String> correctAnswer;
}
