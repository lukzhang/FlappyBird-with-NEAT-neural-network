package flappybirdai;

import static flappybirdai.Pool.POPULATION;


import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 *Contains the main game for flappy bird including the main game loop. Initializes
 * the population of the birds (50) at the start of the game with their own nodes (genes).
 * The game evaluates the birds by looking at the inputs bird position relative to
 * pipe position, and then updates by checking if it the bird flaps as a result of the output
 * and updating the coordinates, and then learns by finding the best bird of the population
 * and creating a new population from the best birds through mutation. The best 
 * birds are based on the farthest distance traveled by keeping track of the number of
 * ticks since the start of the level.
 */
public class FlappyBirdAI extends JPanel implements Runnable{
    
    public static final Random rnd = new Random();

    //Screen dimensions
    private static final int WIDTH = 576;           
    private static final int HEIGHT = 768;   

    //Bird Dimensions
    private static final int BIRD_WIDTH = 72;       
    private static final int BIRD_HEIGHT = 52;
    
    //Floor Dimensions
    private static final int FLOOR_WIDTH = 672;
    private static final int FLOOR_HEIGHT = 224;
    private static final int FLOOR_OFFSET = 96;
    private static final int FLOOR_SPEED = 5;
    
    //Tube Dimensions
    private static final int TUBE_WIDTH = 104;
    private static final int TUBE_HEIGHT = 640;
    private static final int TUBE_APERTURE = 200;

    //Sprites to be used in game (background, birds, ground, tubes)
    private static BufferedImage   BACK_IMAGE;
    private static BufferedImage[] BIRD_IMAGES;
    private static BufferedImage   GROUND_IMAGE;
    private static BufferedImage   TUBE1_IMAGE;
    private static BufferedImage   TUBE2_IMAGE;
    
    //Toggle speed of game
    public static boolean speedUp;
    
    //The bird    
    private static class Bird {
        //Hashmap that links the Species as a key to the 
        private static Map<Species, BufferedImage[]> cache = new WeakHashMap<Species, BufferedImage[]>();

        //The image of the bird. Has a 'color' variable to adjust certain shades
        private static BufferedImage colorBird(final BufferedImage refImage,
                final Color color) {
            
            final BufferedImage image = new BufferedImage(BIRD_WIDTH,
                    BIRD_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            
            final Color bright = color.brighter().brighter();
            final Color dark = color.darker().darker();
            
            for (int y = 0; y < BIRD_HEIGHT; ++y){
                for (int x = 0; x < BIRD_WIDTH; ++x) {
                    int argb = refImage.getRGB(x, y);
                    if (argb == 0xffe0802c)
                        argb = dark.getRGB();
                    else if (argb == 0xfffad78c)
                        argb = bright.getRGB();
                    else if (argb == 0xfff8b733)
                        argb = color.getRGB();
                    image.setRGB(x, y, argb);
                }
            }            
            return image;
        }
    
        //Array of images to be used for each bird
        private BufferedImage[] images;
        
        private final Genome genome;    //bird's neural network
        private double height;          //how high the bird is
        private double velocity;        //bird's vertical velocity
        private double angle;           //angle bird is at
        private boolean flap;           //determines if the bird is flapping
        private int flaps;              //number of flaps bird has had
        private boolean dead;           //determines if the bird is dead

        //Initializes the bird with the species and neural network
        private Bird(final Species species, final Genome genome) {
            if (cache.containsKey(species))
                images = cache.get(species);
            else {
                final Color color = new Color(rnd.nextInt(0x1000000));
                images = new BufferedImage[3];
                for (int i = 0; i < 3; ++i)
                    images[i] = colorBird(BIRD_IMAGES[i], color);
                cache.put(species, images);
            }

            this.genome = genome;   //Sets the genome to the current one
            height = HEIGHT / 2.0;  //Bird starts in the middle of screen
        }
    }

    //The tube obstacles
    private static class Tube {
        
        //Tube's coordinates
        private final double height;        
        private double position;
        //Determines if the bird has passed the tube
        private boolean passed;

        //Initializes the tube by setting its height, and making its position at
        //the rightmost part of the screen when created
        private Tube(final int height) {
            this.height = height;
            position = WIDTH;
            passed = false;
        }
    }
   

    private static final int[]   XS     = new int[] { 2, 6, 14, 18, 26, 50, 54,
            58, 62, 66, 70, 70, 66, 62, 42, 22, 14, 10, 6, 2 };
    private static final int[]   YS     = new int[] { -34, -38, -42, -46, -50,
            -50, -46, -42, -38, -26, -22, -18, -10, -6, -2, -2, -6, -10, -18,
            -22 };
    private static final Polygon BOUNDS = new Polygon(XS, YS, XS.length);

    //Reads the images used for the game
    static {
        try {
            BACK_IMAGE = upscale(ImageIO.read(new File("skyline.png")));
            GROUND_IMAGE = upscale(ImageIO.read(new File("brick.png")));
            final BufferedImage birdImage = ImageIO.read(new File("bird.png"));
            
            //Gets the 3 frame sprite from 'bird.png'
            BIRD_IMAGES = new BufferedImage[] {
                    upscale(birdImage.getSubimage(0, 0, 36, 26)),
                    upscale(birdImage.getSubimage(36, 0, 36, 26)),
                    upscale(birdImage.getSubimage(72, 0, 36, 26)) };
            
            TUBE1_IMAGE = upscale(ImageIO.read(new File("tube1.png")));
            TUBE2_IMAGE = upscale(ImageIO.read(new File("tube2.png")));
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    //Gets bounds to use for collision
    public static Dimension getBounds(final Graphics2D g, final Font font,
            final String text) {
        final int width = (int) font
                .getStringBounds(text, g.getFontRenderContext()).getWidth();
        final int height = (int) font
                .createGlyphVector(g.getFontRenderContext(), text)
                .getVisualBounds().getHeight();
        return new Dimension(width, height);
    }

    public static void main(final String[] args) {
        final JFrame frame = new JFrame();
        frame.addMouseListener(new CustomListener());
        frame.setResizable(false);
        frame.setTitle("Flappy Bird AI");
        frame.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        final FlappyBirdAI ai = new FlappyBirdAI();
        frame.add(ai);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        ai.run();
    }

    //Sets image to buffered image
    private static BufferedImage toBufferedImage(final Image image) {
        final BufferedImage buffered = new BufferedImage(image.getWidth(null),
                image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        buffered.getGraphics().drawImage(image, 0, 0, null);
        return buffered;
    }

    //Scales the image
    private static BufferedImage upscale(final Image image) {
        return toBufferedImage(image.getScaledInstance(image.getWidth(null) * 2,
                image.getHeight(null) * 2, Image.SCALE_FAST));
    }

    private int speed;          //Speed of the game
    private int ticks;          //Number of ticks the current level has had (reset to 0 when all birds are dead)
    private int ticksTubes;     

    private final List<Bird> birds = new ArrayList<Bird>(); //Population of birds
    private final List<Tube> tubes = new ArrayList<Tube>(); //The series of tubes

    private Bird best;      //The best bird of each population (that has travelled the farthest)
    private int  score;     //How many pipes the bird has passed

    //Prepare the inputs for the input Neurons by looing at bird position relative
    //to pipe position
    public void eval() {
        
        //The tube that is coming next
        Tube nextTube = null;
        
        //Looks at each tube's rightmost position and determines if it is greater than
        //then: 1/3 Screen width + middle of bird. In essence, it finds the closest 
        //tube that the bird hasn't crossed yet and sets that as the 'nextTube'
        for (final Tube tube : tubes)
            if (tube.position + TUBE_WIDTH > WIDTH / 3 - BIRD_WIDTH / 2
                    && (nextTube == null || tube.position < nextTube.position))
                nextTube = tube;
        
        //Looks at each bird. If it isn't dead, will give the proper inputs for each
        //of its 4 input neurons
        for (final Bird bird : birds) {
            if (bird.dead)
                continue;

            //array of 4 doubles to be used for the input neurons
            final double[] input = new double[4];
            //First input is relative to current bird's height
            input[0] = bird.height / HEIGHT;
            
            //If there is no tube in sight, set the input values to defaults...
            if (nextTube == null) {
                input[1] = 0.5;
                input[2] = 1.0;
            } 
            //Otherwise, set the input values to next tube's coordinates
            else {
                input[1] = nextTube.height / HEIGHT;
                input[2] = nextTube.position / WIDTH;
            }
            //Fourth input is set to 1.0, which refers to the pipe gap
            input[3] = 1.0;

            //if output is greater than 0.5, the bird flaps
            final double[] output = bird.genome.evaluateNetwork(input);
            if (output[0] > 0.5)
                bird.flap = true;
        }
    }

    //Starts the level with the horizontal speed and adding birds to the current
    //generation
    public void initializeGame() {
        speed = 75;
        ticks = 0;
        ticksTubes = 0;
        best = null;
        score = 0;

        //Make a new pool of birds based on the parameters set in the species'
        //genomes
        birds.clear();
        for (final Species species : Pool.species)
            for (final Genome genome : species.genomes) {
                genome.generateNetwork();
                birds.add(new Bird(species, genome));
            }
        tubes.clear();
    }

    //Finds the best bird in the group if its fitness is greater than the 
    //current 'maxFitness'
    public void learn() {
        best = birds.get(0);
        boolean allDead = true;
        for (final Bird bird : birds) {
            if (bird.dead)
                continue;
            allDead = false;

            //Set fitness to -1.0 to begin with, and then adjusts according to
            //the number of ticks and flaps. Note: the ticks are essentially a marker
            //for distance travelled as the ticks do not reset to 0 until the level
            //resets
            double fitness = ticks - bird.flaps * 1.5;
            fitness = fitness == 0.0 ? -1.0 : fitness;

            //updates the birds fitness (while still alive)
            bird.genome.fitness = fitness;
            if (fitness > Pool.maxFitness)
                Pool.maxFitness = fitness;

            //The best bird's fitness is updated as game progresses
            if (fitness > best.genome.fitness)
                best = bird;
        }

        //If all the birds are dead, start a new generation and restart the level
        if (allDead) {
            Pool.newGeneration();
            initializeGame();
        }
    }

    //Draw the images and text of the game
    @Override
    public void paint(final Graphics g_) {
        final Graphics2D g2d = (Graphics2D) g_;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(BACK_IMAGE, 0, 0, WIDTH, HEIGHT, null);

        for (final Tube tube : tubes) {
            g2d.drawImage(TUBE1_IMAGE, (int) tube.position,
                    HEIGHT - (int) tube.height - TUBE_APERTURE - TUBE_HEIGHT,
                    TUBE_WIDTH, TUBE_HEIGHT, null);
            g2d.drawImage(TUBE2_IMAGE, (int) tube.position,
                    HEIGHT - (int) tube.height, TUBE_WIDTH, TUBE_HEIGHT, null);
        }

        g2d.drawImage(GROUND_IMAGE,
                -(FLOOR_SPEED * ticks % (WIDTH - FLOOR_WIDTH)),
                HEIGHT - FLOOR_OFFSET, FLOOR_WIDTH, FLOOR_HEIGHT, null);

        //Number of birds alive
        int alive = 0;
        final int anim = ticks / 3 % 3;
        
        //For each bird, if not dead increment the number of birds alive
        for (final Bird bird : birds) {
            if (bird.dead)
                continue;
            ++alive;
            
            //Handles rotation of the bird as it moves
            final AffineTransform at = new AffineTransform();
            at.translate(WIDTH / 3 - BIRD_HEIGHT / 3, HEIGHT - bird.height);
            at.rotate(-bird.angle / 180.0 * Math.PI, BIRD_WIDTH / 2,
                    BIRD_HEIGHT / 2);
            //Draws the bird
            g2d.drawImage(bird.images[anim], at, null);
        }
        
    
        //Toggle speed
        g2d.setColor(Color.BLACK);
        Font trb = new Font("TimesRoman", Font.BOLD, 18);
        g2d.setFont(trb);
        g2d.drawString("Click Mouse to Toggle Speed", 160, 700);
        
        //Draw number of birds that are alive
        g2d.drawString("" + alive +"/"+POPULATION + " alive", 470, 50);
        
        //Display fitness
        g2d.drawString("Fitness " + best.genome.fitness + "/" + Pool.maxFitness,
                10, 50);
        
        //Generation
        g2d.drawString("Generation " + Pool.generation, 10, 80);
        
        //Draw score
        g2d.setColor(Color.WHITE);
        trb = new Font("TimesRoman", Font.BOLD, 28);
        g2d.setFont(trb);
        g2d.drawString("" + score, WIDTH/2, 100);
        
    }
    
    //Updates the game by moving along the map and keeps track if each bird needs
    //to flap and update its position.
    public void update() {
        //Increments the ticks and tube ticks
        ++ticks;
        ++ticksTubes;

        //Once ticksTubes is equal to speed, it is time to add a new tube with
        //random height. ticksTubes is reset to 0
        if (ticksTubes == speed) {
            final int height = FLOOR_OFFSET + 100
                    + rnd.nextInt(HEIGHT - 200 - TUBE_APERTURE - FLOOR_OFFSET);
            tubes.add(new Tube(height));
            ticksTubes = 0;
        }

        //Iterates through each tube and determines if it is off screen and needs
        //to be removed. Determines if the bird has passed the current tube and 
        //increments the score
        final Iterator<Tube> it = tubes.iterator();
        while (it.hasNext()) {
            final Tube tube = it.next();
            tube.position -= FLOOR_SPEED;
            if (tube.position + TUBE_WIDTH < 0.0)
                it.remove();
            if (!tube.passed && tube.position + TUBE_WIDTH < WIDTH / 3
                    - BIRD_WIDTH / 2) {
                ++score;
                if (score % 10 == 0) {
                    speed -= 5;
                    speed = Math.max(speed, 20);
                }
                tube.passed = true;
            }
        }

        //Goes through each alive bird and updates its velocity, angle, position
        for (final Bird bird : birds) {
            
            //If bird is dead, go to next bird
            if (bird.dead)
                continue;

            //If bird 'flap' is true, adjust the velocity and set to false
            if (bird.flap) {
                bird.velocity = 10;
                bird.flap = false;
                ++bird.flaps;
            }

            //Adjust the height by adding the current velocity
            bird.height += bird.velocity;
            //Decrease the velocity
            bird.velocity -= 0.98;
            //Adjust the angle to a limit of 90 degrees
            bird.angle = 3.0 * bird.velocity;
            bird.angle = Math.max(-90.0, Math.min(90.0, bird.angle));

            //Make sure bird does not go past the upper bounds of the screen
            if (bird.height > HEIGHT) {
                bird.height = HEIGHT;
                bird.velocity = 0.0;
                bird.angle = -bird.angle;
            }

            //If bird hits the floor, kill the bird
            if (bird.height < FLOOR_OFFSET + BIRD_HEIGHT / 2)
                bird.dead = true;

            //transforms the bird by rotating it
            final AffineTransform at = new AffineTransform();
            at.translate(WIDTH / 3 - BIRD_HEIGHT / 2, HEIGHT - bird.height);
            at.rotate(-bird.angle / 180.0 * Math.PI, BIRD_WIDTH / 2,
                    BIRD_HEIGHT / 2);
            at.translate(0, 52);
            final Shape bounds = new GeneralPath(BOUNDS)
                    .createTransformedShape(at);
            
            //Goes through each tube and determines if the top 'cielTube' or bottom
            //'floorTube' hits the bird. If so, the bird dies
            for (final Tube tube : tubes) {
                final Rectangle2D ceilTube = new Rectangle2D.Double(
                        tube.position,
                        HEIGHT - tube.height - TUBE_APERTURE - TUBE_HEIGHT,
                        TUBE_WIDTH, TUBE_HEIGHT);
                final Rectangle2D floorTube = new Rectangle2D.Double(
                        tube.position, HEIGHT - tube.height, TUBE_WIDTH,
                        TUBE_HEIGHT);
                if (bounds.intersects(ceilTube)
                        || bounds.intersects(floorTube)) {
                    bird.dead = true;
                    break;
                }
            }
        }
    }

    @Override
    //Runs the game. Contains the overall game loop
    public void run() {
        //Initialize the bird polulation
        Pool.initializePool();
        //Initialize the game
        initializeGame();
        
        //Main game loop
        while (true) {
            //Sequence of evaluating for each bird its coordinate as well as the
            //next tube's, updates the game by detecting collisions and if the bird
            //should flap, and learns by determining the fitness of the best bird. 
            eval();
            update();
            learn();

            //Redraws the game
            repaint();
            
            //Adjusts the speed of the game by reducing the sleeptime. Adjusted if 
            //user clicks mouse button
            try {
                if(!speedUp)
                    Thread.sleep(20L);
                else
                    Thread.sleep(2L);
            } catch (final InterruptedException e) {
            }
        }
    }

    
    
}
