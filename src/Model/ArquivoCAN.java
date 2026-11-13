package Model;

/** Dados de ArquivoCAN conforme o banco filtragemdados. */
public class ArquivoCAN {
    private long id;
    private String nomeArquivo;
    private String modeloValvula;
    private java.time.LocalDateTime dataImportacao;
    private String sha256;
    private String statusImportacao;

    public ArquivoCAN() {
    }

    public ArquivoCAN(long id, String nomeArquivo, String modeloValvula, java.time.LocalDateTime dataImportacao, String sha256, String statusImportacao) {
        this.id = id;
        this.nomeArquivo = nomeArquivo;
        this.modeloValvula = modeloValvula;
        this.dataImportacao = dataImportacao;
        this.sha256 = sha256;
        this.statusImportacao = statusImportacao;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public void setNomeArquivo(String nomeArquivo) {
        this.nomeArquivo = nomeArquivo;
    }

    public String getModeloValvula() {
        return modeloValvula;
    }

    public void setModeloValvula(String modeloValvula) {
        this.modeloValvula = modeloValvula;
    }

    public java.time.LocalDateTime getDataImportacao() {
        return dataImportacao;
    }

    public void setDataImportacao(java.time.LocalDateTime dataImportacao) {
        this.dataImportacao = dataImportacao;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getStatusImportacao() {
        return statusImportacao;
    }

    public void setStatusImportacao(String statusImportacao) {
        this.statusImportacao = statusImportacao;
    }

    @Override
    public String toString() {
        return nomeArquivo == null ? "" : nomeArquivo;
    }
}

