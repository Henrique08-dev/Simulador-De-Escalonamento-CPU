import controller.SimuladorController;
import model.TipoAlgoritmo;

public class Main {
    public static void main(String[] args) {
        SimuladorController controller = new SimuladorController();

        controller.iniciarSimulacao(
            TipoAlgoritmo.SJF, 
            2,          
            1000,        
            2          
        );
    }
}