package vn.actvn.onthionline.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.actvn.onthionline.service.dto.ExamInfoDto;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetExamBySubjectResponse {
    private List<ExamInfoDto> exam;
}
