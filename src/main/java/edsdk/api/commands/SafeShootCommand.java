package edsdk.api.commands;

import java.io.File;
import java.io.IOException;

import com.sun.jna.Pointer;

import edsdk.api.CanonCommand;
import edsdk.bindings.EdSdkLibrary.EdsBaseRef;
import edsdk.bindings.EdSdkLibrary.EdsDirectoryItemRef;
import edsdk.utils.CanonConstants.EdsCameraCommand;
import edsdk.utils.CanonConstants.EdsError;
import edsdk.utils.CanonConstants.EdsImageType;
import edsdk.utils.CanonConstants.EdsObjectEvent;
import edsdk.utils.CanonConstants.EdsPropertyID;
import edsdk.utils.CanonConstants.EdsSaveTo;
import edsdk.utils.CanonConstants.EdsShutterButton;
import edsdk.utils.CanonUtils;

public class SafeShootCommand extends CanonCommand<File[]> {

    private final EdsSaveTo saveTo;
    private final boolean appendFileExtension;
    private File[] dest = null;
    private boolean oldEvfMode;
    private int shotAttempts;
    private int count;
    private int processedFiles = 0;

    public SafeShootCommand() {
        this( EdsSaveTo.kEdsSaveTo_Both );
    }

    public SafeShootCommand( final EdsSaveTo saveTo ) {
        this( saveTo, Integer.MAX_VALUE, (File[]) null, false );
    }

    public SafeShootCommand( final EdsSaveTo saveTo, final int shotAttempts ) {
        this( saveTo, shotAttempts, (File[]) null, false );
    }

    public SafeShootCommand( final EdsSaveTo saveTo, final int shotAttempts,
                         final File dest ) {
        this( saveTo, shotAttempts, new File[] { dest }, false );
    }

    public SafeShootCommand( final EdsSaveTo saveTo, final int shotAttempts,
                         final File[] dest ) {
        this( saveTo, shotAttempts, dest, false );
    }

    public SafeShootCommand( final EdsSaveTo saveTo, final int shotAttempts,
                         final File[] dest, final boolean appendFileExtension ) {
        this.saveTo = saveTo;
        this.shotAttempts = shotAttempts;
        this.appendFileExtension = appendFileExtension;
        this.count = 1;

        if ( dest != null && ( dest.length < 0 || dest.length > 2 ) ) {
            throw new IllegalArgumentException( "dest must contain one or two file paths" );
        }
        this.dest = dest;
    }

    @Override
    public void run() {
        EdsError err = EdsError.EDS_ERR_OK;

        if ( camera.getEdsCamera() != null ) {
            // Check if there is more than one image
            final long imageQuality;
            try {
                imageQuality = CanonUtils.getPropertyData( camera.getEdsCamera(), EdsPropertyID.kEdsPropID_ImageQuality );
                final EdsImageType secondaryImageType = EdsImageType.enumOfValue( (int) ( imageQuality >>> 4 & 0xf ) );
                if ( secondaryImageType != EdsImageType.kEdsImageType_Unknown ) {
                    count = 2;
                }
            } catch ( Exception e ) {
                System.err.println("SafeShootCommand: Failed to get image quality: " + e.getMessage());
            }

            if ( dest == null ) {
                dest = new File[count];
            } else if ( dest.length < count ) {
                dest = new File[] { dest[0], null };
            }

            try {
                err = CanonUtils.setPropertyData( camera.getEdsCamera(), EdsPropertyID.kEdsPropID_SaveTo, saveTo );
                if ( err == EdsError.EDS_ERR_OK && !EdsSaveTo.kEdsSaveTo_Camera.equals( saveTo ) ) {
                    CanonUtils.setCapacity( camera.getEdsCamera() );
                }
            } catch(Exception e) {
                System.err.println("SafeShootCommand: Failed to set saveTo: " + e.getMessage());
            }

            err = EdsError.EDS_ERR_UNIMPLEMENTED;
            while ( shotAttempts > 0 && err != EdsError.EDS_ERR_OK ) {
                try {
                    oldEvfMode = CanonUtils.isLiveViewEnabled( camera.getEdsCamera(), true );
                    if ( oldEvfMode ) {
                        CanonUtils.endLiveView( camera.getEdsCamera() );
                    }
                } catch(Exception e) {}
                
                err = sendCommand( EdsCameraCommand.kEdsCameraCommand_TakePicture, 0 );
                if ( err != EdsError.EDS_ERR_OK ) {
                    try {
                        Thread.sleep( 1000 );
                    }
                    catch ( final InterruptedException e ) {
                        Thread.currentThread().interrupt(); 
                        return;
                    }
                } else {
                    try {
                        if ( CanonUtils.isMirrorLockupEnabled( camera.getEdsCamera() ) ) {
                            sendCommand( EdsCameraCommand.kEdsCameraCommand_PressShutterButton, EdsShutterButton.kEdsCameraCommand_ShutterButton_Completely_NonAF );
                            sendCommand( EdsCameraCommand.kEdsCameraCommand_PressShutterButton, EdsShutterButton.kEdsCameraCommand_ShutterButton_OFF );
                        }
                    } catch(Exception e) {}
                }
                shotAttempts--;
            }
        }
        if ( err == EdsError.EDS_ERR_OK ) {
            notYetFinished();
        } else {
            System.err.println("SafeShootCommand: Failed to execute TakePicture command.");
            finish();
        }
    }

    @Override
    public EdsError apply( final EdsObjectEvent inEvent,
                           final EdsBaseRef inRef, final Pointer inContext ) {
        return apply( inEvent, new EdsDirectoryItemRef( inRef.getPointer() ), inContext );
    }

    public EdsError apply( final EdsObjectEvent inEvent,
                           final EdsDirectoryItemRef inRef,
                           final Pointer inContext ) {
        if ( inEvent == EdsObjectEvent.kEdsObjectEvent_DirItemCreated ||
             inEvent == EdsObjectEvent.kEdsObjectEvent_DirItemRequestTransfer ) {
            
            if (processedFiles >= dest.length) {
                System.out.println("SafeShootCommand: Ignored extra event " + inEvent.name() + " (processedFiles=" + processedFiles + ")");
                return EdsError.EDS_ERR_OK;
            }

            System.out.println( "Camera saved an image file (event=" + inEvent.name() + ")" );
            if ( !EdsSaveTo.kEdsSaveTo_Camera.equals( saveTo ) ) {
                try {
                    dest[processedFiles] = CanonUtils.download( inRef, dest[processedFiles], appendFileExtension );
                } catch(Exception e) {
                    System.err.println("SafeShootCommand: Failed to download file: " + e.getMessage());
                }
            }
            
            processedFiles++;
            
            if ( processedFiles >= count ) {
                setResult( dest );
                try {
                    if ( oldEvfMode ) {
                        CanonUtils.beginLiveView( camera.getEdsCamera() );
                    }
                } catch(Exception e) {}
                finish();
            }
        }

        return EdsError.EDS_ERR_OK;
    }
}
