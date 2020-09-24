package vn.actvn.onthionline.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.actvn.onthionline.client.dto.*;
import vn.actvn.onthionline.common.Constant;
import vn.actvn.onthionline.common.ValidationError;
import vn.actvn.onthionline.common.exception.ServiceException;
import vn.actvn.onthionline.common.exception.ServiceExceptionBuilder;
import vn.actvn.onthionline.common.exception.ValidationErrorResponse;
import vn.actvn.onthionline.common.utils.OptimizedPage;
import vn.actvn.onthionline.common.utils.ServiceUtil;
import vn.actvn.onthionline.domain.*;
import vn.actvn.onthionline.repository.ExamHistoryRepository;
import vn.actvn.onthionline.repository.ExamQuestionRepository;
import vn.actvn.onthionline.repository.ExamRepository;
import vn.actvn.onthionline.repository.UserRepository;
import vn.actvn.onthionline.service.dto.*;
import vn.actvn.onthionline.service.mapper.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ExamService {
    private final Logger LOGGER = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamQuestionRepository examQuestionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExamHistoryRepository examHistoryRepository;

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private ExamQuestionMapper examQuestionMapper;

    @Autowired
    private ExamForUserMapper examForUserMapper;

    @Autowired
    private ExamAnswerMapper examAnswerMapper;

    @Autowired
    private HistoryResultMapper historyResultMapper;

    @Autowired
    private ExamHistoryMapper examHistoryMapper;

    @Transactional(rollbackFor = Throwable.class)
    public AddExamResponse add(AddExamRequest request, String username) throws ServiceException {
        try {
            if (null == request) ServiceUtil.generateEmptyPayloadError();
            if (null == request.getExam())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Exam", ValidationError.NotNull))
                        .build();

            // Save exam
            Exam exam = examMapper.toEntity(request.getExam());
            exam.setNumQuestion(request.getExam().getExamQuestions().size());
            exam.setNumPeopleDid(0);
            exam.setActive(false);
            exam.setUserCreated(username);
            exam.setCreatedDate(new Date());
            Exam examSaved = examRepository.save(exam);
            LOGGER.info("Save exam {}", examSaved);

            // Save question
            List<ExamQuestion> examQuestions = request.getExam().getExamQuestions()
                    .stream().map(examQuestionMapper::toEntity).collect(Collectors.toList());
            examQuestions.stream().forEach(question -> {
                question.setExam(examSaved);
                question.setCreatedDate(new Date());
            });
            List<ExamQuestion> examQuestionsSaved = examQuestionRepository.saveAll(examQuestions);

            AddExamResponse response = new AddExamResponse();
            response.setExam(examMapper.toDtoWithQuestion(examSaved, examQuestionsSaved));

            return response;
        } catch (ServiceException e) {
            throw e;
        }
    }

    public GetExamBySubjectResponse getExamBySubject(GetExamBySubjectRequest request, String username) throws ServiceException {
        try {
            if (null == request) ServiceUtil.generateEmptyPayloadError();
            if (null == request.getSubject())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Subject", ValidationError.NotNull))
                        .build();

            Optional<List<Exam>> exams = examRepository.findAllExamActiveBySubjectAndGrade(request.getSubject(), request.getGrade());
            GetExamBySubjectResponse response = new GetExamBySubjectResponse();
            List<ExamInfoDto> examInfoDtos = new ArrayList<>();

            if (!exams.isPresent()) {
                response.setExam(examInfoDtos);
                return response;
            }

            examInfoDtos = exams.get().stream().map(ExamInfoMapper::toDto).collect(Collectors.toList());

            if (null != username) {
                User user = userRepository.findByUsername(username);
                examInfoDtos.stream().forEach(exam -> {
                    Optional<ExamHistory> examHistory = examHistoryRepository.findLastHistory(exam.getId(), user.getId());
                    LOGGER.info("Get last history exam {}", examHistory);
                    if (!examHistory.isPresent())
                        exam.setLastHistory(null);
                    else
                        exam.setLastHistory(examHistoryMapper.toDto(examHistory.get()));
                });
            }

            LOGGER.info("Get list exam by subject");
            response.setExam(examInfoDtos);
            return response;
        } catch (ServiceException e) {
            throw e;
        }
    }

    public GetAllExamResponse getAllExam(GetAllExamRequest request) throws ServiceException {
        try {
            if (null == request)
                ServiceUtil.generateEmptyPayloadError();
            if (request.getPageNumber() < 0)
                request.setPageNumber(0);
            if (request.getPageSize() < 1)
                request.setPageSize(Constant.DEFAULT_PAGE_SIZE);
            Page<Exam> exams = examRepository.findAllExam(PageRequest.of(request.getPageNumber(), request.getPageSize()), request.getSearch());
            Page<ExamDto> examDtos = exams.map(examMapper::toDto);
            GetAllExamResponse response = new GetAllExamResponse();
            response.setExamDtos(OptimizedPage.convert(examDtos));
            return response;
        } catch (ServiceException e) {
            throw e;
        }
    }

    public GetExamResponse getExam(GetExamRequest request) throws ServiceException {
        try {
            if (null == request) ServiceUtil.generateEmptyPayloadError();
            if (null == request.getId())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Id", ValidationError.NotNull))
                        .build();

            Optional<Exam> exam = examRepository.findById(request.getId());

            if (!exam.isPresent())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Id", ValidationError.Invalid))
                        .build();
            LOGGER.info("Get exam {}",exam);
            GetExamResponse response = new GetExamResponse();
            response.setExam(examMapper.toDto(exam.get()));
            return response;
        } catch (ServiceException e) {
            throw e;
        }
    }

    public GetExamFromUserResponse getExamForUser(GetExamFromUserRequest request) throws ServiceException {
        try {
            if (null == request) ServiceUtil.generateEmptyPayloadError();
            if (null == request.getId())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Id", ValidationError.NotNull))
                        .build();

            Optional<Exam> exam = examRepository.findExamActiveById(request.getId());

            if (!exam.isPresent())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Id", ValidationError.Invalid))
                        .build();
            LOGGER.info("Get exam user {}", exam);
            GetExamFromUserResponse response = new GetExamFromUserResponse();
            response.setExam(examForUserMapper.toDto(exam.get()));
            return response;
        } catch (ServiceException e) {
            throw e;
        }
    }

    public DoExamResponse doExam(DoExamRequest request, String username) throws ServiceException {
        try {
            if (null == request) ServiceUtil.generateEmptyPayloadError();
            if (null == request.getId())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Id", ValidationError.NotNull))
                        .build();
            if (null == request.getTime())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Time", ValidationError.NotNull))
                        .build();
            if (null == request.getExamAnswer())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Exam Answer", ValidationError.NotNull))
                        .build();
            Optional<Exam> exam = examRepository.findExamActiveById(request.getId());
            User user = userRepository.findByUsername(username);
            if (!exam.isPresent())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Id", ValidationError.Invalid))
                        .build();
            LOGGER.info("{} do exam {}", username, exam);
            //Check correct answer
            Integer numOfCorrect = 0;
            for (ExamAnswerDto answer : request.getExamAnswer()) {
                Optional<ExamQuestion> question = examQuestionRepository.findById(answer.getQuestionId());
                if (!question.isPresent()) continue;
                if (question.get().getCorrectAnswer().equalsIgnoreCase(answer.getAnswer())) numOfCorrect++;
            }

            //Update exam history
            Integer countHistory = examHistoryRepository.countAllByExamIdAndUserId(request.getId(), user.getId());
            ExamHistory newExamHistory = new ExamHistory();
            if (countHistory > 0)
                newExamHistory.setName(exam.get().getName() + Constant.DO_EXAM + countHistory + ")");
            else
                newExamHistory.setName(exam.get().getName());
            newExamHistory.setTime(request.getTime());
            newExamHistory.setNumCorrectAns(numOfCorrect);
            newExamHistory.setNumAns(request.getExamAnswer().size());
            newExamHistory.setExamAnswers(examAnswerMapper.toListEntity(request.getExamAnswer()));
            newExamHistory.setCreatedDate(new Date());
            newExamHistory.setUserCreated(user);
            newExamHistory.setExam(exam.get());
            ExamHistory savedHistory = examHistoryRepository.saveAndFlush(newExamHistory);
            LOGGER.info("New history {}", newExamHistory);

            //Update exam
            exam.get().setNumPeopleDid(exam.get().getNumPeopleDid()+1);
            examRepository.saveAndFlush(exam.get());

            DoExamResponse response = new DoExamResponse();
            response.setResult(historyResultMapper.toDto(savedHistory));
            return response;
        } catch (ServiceException e) {
            throw e;
        }
    }

    public GetResultResponse getResult(GetResultRequest request, String username) throws ServiceException {
        try {
            if (null == request) ServiceUtil.generateEmptyPayloadError();
            if (null == request.getHistoryId())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Id", ValidationError.NotNull))
                        .build();

            User user = userRepository.findByUsername(username);
            Optional<ExamHistory> history = examHistoryRepository.findByIdAndUserId(request.getHistoryId(), user.getId());
            if (!history.isPresent())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Id", ValidationError.Invalid))
                        .build();
            LOGGER.info("Get result {}", history);
            GetResultResponse response = new GetResultResponse();
            response.setResult(historyResultMapper.toDto(history.get()));

            return response;
        } catch (ServiceException e) {
            throw e;
        }
    }

    public ChangeActiveExamResponse changeActive(ChangeActiveExamRequest request) throws ServiceException {
        try {
            if (null == request) ServiceUtil.generateEmptyPayloadError();
            if (null == request.getId())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Id", ValidationError.NotNull))
                        .build();
            if (null == request.getIsActive())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("IsActive", ValidationError.NotNull))
                        .build();

            Optional<Exam> exam = examRepository.findById(request.getId());
            if (!exam.isPresent())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Id", ValidationError.Invalid))
                        .build();
            exam.get().setActive(request.getIsActive());

            ChangeActiveExamResponse response = new ChangeActiveExamResponse();
            Optional<Exam> updated = Optional.of(
                    Optional.of(examRepository.saveAndFlush(exam.get())))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(examUpdate -> {
                        LOGGER.info("Change active exam {}", examUpdate);
                        response.setSuccess(true);
                        return examUpdate;
                    });

            return response;
        } catch (ServiceException e) {
            throw e;
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    public UpdateExamResponse update(UpdateExamRequest request) throws ServiceException {
        try {
            if (null == request) ServiceUtil.generateEmptyPayloadError();
            if (null == request.getExam())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Exam", ValidationError.NotNull))
                        .build();

            Optional<Exam> exam = examRepository.findById(request.getExam().getId());

            // Update list question
            List<ExamQuestion> examQuestions = request.getExam().getExamQuestions()
                    .stream().map(examQuestionMapper::toEntity).collect(Collectors.toList());
            examQuestions.stream().forEach(question -> {
                if (null != question.getId()) {
                    Optional<ExamQuestion> examQuestion = examQuestionRepository.findByIdAndExamId(question.getId(), exam.get().getId());
                    if (examQuestion.isPresent()) {
                        question.setCreatedDate(examQuestion.get().getCreatedDate());
                        question.setUpdatedDate(new Date());
                        question.setExam(examQuestion.get().getExam());
                    }
                } else {
                    question.setCreatedDate(new Date());
                    question.setExam(exam.get());
                }
            });
            examQuestionRepository.saveAll(examQuestions);

            // Update exam
            Exam newExam = examMapper.toEntity(request.getExam());
            newExam.setNumPeopleDid(exam.get().getNumPeopleDid());
            newExam.setNumQuestion(request.getExam().getExamQuestions().size());
            newExam.setActive(exam.get().isActive());
            newExam.setUserCreated(exam.get().getUserCreated());
            newExam.setCreatedDate(exam.get().getCreatedDate());
            newExam.setUpdatedDate(new Date());
            newExam.setExamHistory(exam.get().getExamHistory());
            newExam.setComments(exam.get().getComments());

            Optional<Exam> updatedExam = Optional.of(
                    Optional.of(examRepository.saveAndFlush(newExam)))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(examUpdate -> {
                        LOGGER.info("Update exam {}", examUpdate);
                        return examUpdate;
                    });


            UpdateExamResponse response = new UpdateExamResponse();
            response.setExam(examMapper.toDto(updatedExam.get()));
            return response;
        } catch (ServiceException e) {
            throw e;
        }
    }

    public GetRankingInExamResponse rankingByExam(GetRankingInExamRequest request) throws ServiceException {
        try {
            if (null == request) ServiceUtil.generateEmptyPayloadError();
            if (null == request.getExamId())
                throw ServiceExceptionBuilder.newBuilder()
                        .addError(new ValidationErrorResponse("Exam Id", ValidationError.NotNull))
                        .build();

            List<ExamHistory> examHistories = examHistoryRepository.findListHigherCorrectAnswerInExam(request.getExamId());
            List<RankingDto> rankingDtos = new ArrayList<>();
            examHistories.stream().forEach(history -> {
                RankingDto rankingDto = new RankingDto();
                rankingDto.setFullName(history.getUserCreated().getFullname());
                rankingDto.setNumCorrectAns(history.getNumCorrectAns());
                rankingDto.setTotalQuestion(history.getExam().getNumQuestion());
                rankingDtos.add(rankingDto);
            });

            GetRankingInExamResponse response = new GetRankingInExamResponse();
            response.setRanking(rankingDtos);
            return response;
        } catch (ServiceException e) {
            throw e;
        }
    }

    public DeleteExamResponse deleteExam(DeleteExamRequest request) throws ServiceException {
        try {
            if (null == request) ServiceUtil.generateEmptyPayloadError();

            List<DeleteExamDto> deleteExamDtos = new ArrayList<>();
            for (Integer id : request.getExamIds()) {
                DeleteExamDto deleteExamDto = new DeleteExamDto();
                deleteExamDto.setId(id);
                Optional<Exam> exam = examRepository.findById(id);
                if (!exam.isPresent()) {
                    deleteExamDto.setSuccess(false);
                    deleteExamDto.setError("Exam Id invalid");
                    deleteExamDtos.add(deleteExamDto);
                    continue;
                }
                if (exam.get().getExamHistory().size() > 0) {
                    deleteExamDto.setSuccess(false);
                    deleteExamDto.setError("Exam can't be delete");
                    deleteExamDtos.add(deleteExamDto);
                    continue;
                }
                exam.get().getExamQuestions().forEach(question -> {
                    examQuestionRepository.delete(question);
                });
                examRepository.delete(exam.get());
                deleteExamDto.setSuccess(true);
                deleteExamDto.setError(null);
                deleteExamDtos.add(deleteExamDto);
            }
            DeleteExamResponse response = new DeleteExamResponse();
            response.setDeleteExamDtos(deleteExamDtos);
            return response;
        } catch (ServiceException e) {
            throw e;
        }
    }

    public GetCompletedExamResponse getCompletedExam(String username) {
        User user = userRepository.findByUsername(username);
        List<CompletedExamDto> completedExamDtos = new ArrayList<>();
        List<Integer> examId = examHistoryRepository.findAllExamIdByUserId(user.getId());
        examId.stream().forEach(id -> {
            Optional<ExamHistory> examHistory = examHistoryRepository.findLastHistory(id, user.getId());
            CompletedExamDto completedExamDto = new CompletedExamDto();
            completedExamDto.setId(examHistory.get().getExam().getId());
            completedExamDto.setLastHistory(examHistoryMapper.toDto(examHistory.get()));
            completedExamDto.setName(examHistory.get().getExam().getName());
            completedExamDtos.add(completedExamDto);
        });

        GetCompletedExamResponse response = new GetCompletedExamResponse();
        response.setCompletedExamDtos(completedExamDtos);
        return response;
    }
}
