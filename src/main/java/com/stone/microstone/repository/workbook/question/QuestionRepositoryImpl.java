package com.stone.microstone.repository.workbook.question;

import com.stone.microstone.domain.entitiy.Question;
import com.stone.microstone.domain.entitiy.WorkBook;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class QuestionRepositoryImpl implements QuestionRepository {

    @PersistenceContext
    private EntityManager em;

    public QuestionRepositoryImpl(EntityManager em) {
        this.em = em;
    }


    @Override
    public Question save(Question question) {
        if(question.getPr_id() == 0){
            em.persist(question);
        }else{
            em.merge(question);
        }
        return question;
    }

    @Override
    public Optional<Question> findById(int id) {
        return em.createQuery("SELECT q FROM Question q WHERE q.pr_id=:id",Question.class)
                .setParameter("id",id)
                .getResultList().stream().findFirst();
    }

    //문제집 기반 찾기.
    @Override
    public List<Question> findAllwithWorkBook(WorkBook workBook) {
        return em.createQuery("SELECT q FROM Question q WHERE q.workBook=:workBook order by pr_wb_id",Question.class)
                .setParameter("workBook",workBook)
                .getResultList();
    }

    //전체 문제 조회
    @Override
    public List<Question> findAll() {
        return em.createQuery("SELECT q FROM Question q",Question.class)
                .getResultList();
    }



    @Override
    public Optional<Question> findLastQuestion(WorkBook workBook) {
        return em.createQuery("SELECT MIN(q.pr_id) FROM Question q WHERE q.workBook=:workBook",Question.class)
                .setParameter("workBook",workBook)
                .getResultList().stream().findFirst();
    }


}
