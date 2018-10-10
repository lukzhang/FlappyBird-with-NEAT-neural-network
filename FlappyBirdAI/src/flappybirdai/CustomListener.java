package flappybirdai;

import static flappybirdai.FlappyBirdAI.speedUp;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

//Mouselistener so that when player clicks on screen, can toggle the speed
public class CustomListener implements MouseListener{

      public void mouseClicked(MouseEvent e) {
          speedUp = !speedUp;       //Global variable located in FlappyBirdAI class
      }

      public void mousePressed(MouseEvent e) {
      }

      public void mouseReleased(MouseEvent e) {
      }

      public void mouseEntered(MouseEvent e) {
      }

      public void mouseExited(MouseEvent e) {
      }
   }
