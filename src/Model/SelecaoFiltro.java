package Model;

/** Dados da selecao; idCan nulo representa todos os IDs do arquivo. */
public class SelecaoFiltro {
    private long arquivoId;
    private String idCan;

    public SelecaoFiltro() {
    }

    public SelecaoFiltro(long arquivoId, String idCan) {
        this.arquivoId = arquivoId;
        this.idCan = idCan;
    }

    public long getArquivoId() {
        return arquivoId;
    }

    public void setArquivoId(long arquivoId) {
        this.arquivoId = arquivoId;
    }

    public String getIdCan() {
        return idCan;
    }

    public void setIdCan(String idCan) {
        this.idCan = idCan;
    }
}

