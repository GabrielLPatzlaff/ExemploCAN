package Model;

/** Dados de MensagemCAN conforme o banco filtragemdados. */
public class MensagemCAN {
    private long id;
    private int numeroRegistro;
    private java.time.LocalTime horario;
    private String direcao;
    private String idCan;
    private Integer dlc;
    private String dadosHex;
    private String asciiDados;
    private String deltaDt;
    private long arquivoId;

    public MensagemCAN() {
    }

    public MensagemCAN(long id, int numeroRegistro, java.time.LocalTime horario, String direcao, String idCan, Integer dlc, String dadosHex, String asciiDados, String deltaDt, long arquivoId) {
        this.id = id;
        this.numeroRegistro = numeroRegistro;
        this.horario = horario;
        this.direcao = direcao;
        this.idCan = idCan;
        this.dlc = dlc;
        this.dadosHex = dadosHex;
        this.asciiDados = asciiDados;
        this.deltaDt = deltaDt;
        this.arquivoId = arquivoId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getNumeroRegistro() {
        return numeroRegistro;
    }

    public void setNumeroRegistro(int numeroRegistro) {
        this.numeroRegistro = numeroRegistro;
    }

    public java.time.LocalTime getHorario() {
        return horario;
    }

    public void setHorario(java.time.LocalTime horario) {
        this.horario = horario;
    }

    public String getDirecao() {
        return direcao;
    }

    public void setDirecao(String direcao) {
        this.direcao = direcao;
    }

    public String getIdCan() {
        return idCan;
    }

    public void setIdCan(String idCan) {
        this.idCan = idCan;
    }

    public Integer getDlc() {
        return dlc;
    }

    public void setDlc(Integer dlc) {
        this.dlc = dlc;
    }

    public String getDadosHex() {
        return dadosHex;
    }

    public void setDadosHex(String dadosHex) {
        this.dadosHex = dadosHex;
    }

    public String getAsciiDados() {
        return asciiDados;
    }

    public void setAsciiDados(String asciiDados) {
        this.asciiDados = asciiDados;
    }

    public String getDeltaDt() {
        return deltaDt;
    }

    public void setDeltaDt(String deltaDt) {
        this.deltaDt = deltaDt;
    }

    public long getArquivoId() {
        return arquivoId;
    }

    public void setArquivoId(long arquivoId) {
        this.arquivoId = arquivoId;
    }
}

