package com.vela.im.service.knowledge.domain.service.vector;

/**
 * 向量检索命中项
 */
public class VectorHit {

    private Long docId;
    private Integer chunkNo;
    private String content;
    private float score;

    public VectorHit() {
    }

    public VectorHit(Long docId, Integer chunkNo, String content, float score) {
        this.docId = docId;
        this.chunkNo = chunkNo;
        this.content = content;
        this.score = score;
    }

    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public Integer getChunkNo() { return chunkNo; }
    public void setChunkNo(Integer chunkNo) { this.chunkNo = chunkNo; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public float getScore() { return score; }
    public void setScore(float score) { this.score = score; }
}
